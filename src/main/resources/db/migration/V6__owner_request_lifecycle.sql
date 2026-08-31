-- V6: Owner Request Lifecycle Indexes and Constraints

CREATE INDEX idx_offers_state_expires ON offers(state, expires_at);
CREATE INDEX idx_offers_request_state ON offers(request_id, state);
CREATE INDEX idx_appointments_owner_start ON appointments(owner_id, start_at);
CREATE INDEX idx_appointments_pet_start ON appointments(pet_id, start_at);
CREATE INDEX idx_appointments_booking_start ON appointments(booking_state, start_at);
CREATE INDEX idx_owner_history_owner_time ON owner_history_events(owner_id, occurred_at);
CREATE INDEX idx_workflow_rev_req_num ON workflow_revisions(request_id, revision_number);
CREATE INDEX idx_jobs_wf_state ON background_jobs(workflow_revision_id, state);
