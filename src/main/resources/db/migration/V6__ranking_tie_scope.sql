ALTER TABLE tie_decisions ADD COLUMN applies_to_category BOOLEAN NOT NULL DEFAULT TRUE;

-- Decisions made separately in subgroups do not establish an order across those subgroups.
UPDATE tie_decisions decision
SET applies_to_category = FALSE
WHERE EXISTS (
    SELECT 1
    FROM enrollments enrolled
    JOIN children child ON child.id = enrolled.child_id
    JOIN tie_decisions peer_decision ON peer_decision.marathon_id = enrolled.marathon_id
    JOIN children peer ON peer.id = peer_decision.child_id AND peer.category = child.category
    JOIN enrollments peer_enrolled
        ON peer_enrolled.marathon_id = peer_decision.marathon_id
        AND peer_enrolled.child_id = peer.id
    WHERE enrolled.marathon_id = decision.marathon_id
        AND enrolled.child_id = decision.child_id
        AND peer_decision.score = decision.score
        AND peer_enrolled.competition_category <> enrolled.competition_category
);
