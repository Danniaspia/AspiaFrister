-- Aspia Frister – database til momsestimater.
-- Kør hele filen i Supabase → SQL Editor → New query → Run.
--
-- ⚠️ TESTVERSION: alle med den offentlige nøgle kan læse og rette alle rækker.
--    Brug KUN fiktive tal. Inden rigtige tal: slå login til for bogholdere og
--    giv kunderne en aktiveringskode (se nederst).

create table if not exists public.customers (
    cvr               text primary key check (cvr ~ '^[0-9]{8}$'),
    company_name      text not null default '',
    vat_frequency     text not null default 'HALF_YEAR',
    estimate_amount   numeric(14, 2),
    estimate_period   text,
    estimate_deadline date,
    estimate_note     text,
    updated_at        timestamptz not null default now(),
    updated_by        text
);

-- updated_at sættes kun, når estimatet ændres – så appen ikke giver notifikation,
-- bare fordi kunden selv har gemt sin opsætning igen.
create or replace function public.touch_estimate() returns trigger
language plpgsql as $$
begin
    if tg_op = 'INSERT'
       or new.estimate_amount   is distinct from old.estimate_amount
       or new.estimate_period   is distinct from old.estimate_period
       or new.estimate_deadline is distinct from old.estimate_deadline
       or new.estimate_note     is distinct from old.estimate_note then
        new.updated_at := now();
    else
        new.updated_at := old.updated_at;
    end if;
    return new;
end $$;

drop trigger if exists customers_touch on public.customers;
create trigger customers_touch before insert or update on public.customers
    for each row execute function public.touch_estimate();

alter table public.customers enable row level security;

-- TEST: åben adgang for den offentlige nøgle (anon).
drop policy if exists "test read"   on public.customers;
drop policy if exists "test insert" on public.customers;
drop policy if exists "test update" on public.customers;
drop policy if exists "test delete" on public.customers;
create policy "test read"   on public.customers for select to anon using (true);
create policy "test insert" on public.customers for insert to anon with check (true);
create policy "test update" on public.customers for update to anon using (true) with check (true);
create policy "test delete" on public.customers for delete to anon using (true);

-- Senere (rigtige tal):
--   1. Drop de fire "test"-policies.
--   2. Bogholdere logger ind (Authentication → Users, kun @aspia.dk) og får fuld adgang:
--        create policy "aspia staff" on public.customers for all to authenticated
--          using (auth.jwt() ->> 'email' like '%@aspia.dk') with check (auth.jwt() ->> 'email' like '%@aspia.dk');
--   3. Kunder læser kun deres egen række via en hemmelig aktiveringskode og en
--      security definer-funktion – ikke via CVR.
