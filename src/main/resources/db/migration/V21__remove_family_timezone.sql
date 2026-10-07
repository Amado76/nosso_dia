-- Dates and routine times are local calendar values; event timestamps remain UTC instants.
ALTER TABLE beehome.families DROP COLUMN timezone;
