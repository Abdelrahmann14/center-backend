-- Re-seed the group and center feeds after fixing the shape of the pulled row.
--
-- Both resolvers emitted is_active only under the old alias `active`, which the
-- mobile attendance screen reads but the web does not: every group and every
-- center therefore arrived with is_active undefined, which is falsy, so the
-- schedule and the center cards rendered everything as disabled. groupRow was
-- also missing last_attendance, which the clients cannot recompute because
-- attendance is written by lesson registration rather than by that page.
--
-- Same reasoning as V57: fixing the resolver is not enough on its own, because
-- pull only sends rows past the client's cursor. The rows already mirrored keep
-- their wrong values until the row happens to be edited, so push every one
-- through the feed once more and let the corrected row replace it.
update groups set updated_at = updated_at;
update centers set updated_at = updated_at;
