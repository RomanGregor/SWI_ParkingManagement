-- Demo data so that the walking skeleton can be exercised straight after `docker/podman up`.
-- Fixed UUIDs so the curl examples in README.md keep working.

insert into app_user (id, email, full_name, role, accessibility_permit_valid_until) values
    ('11111111-1111-1111-1111-111111111111', 'driver@example.edu',  'Demo Driver',   'DRIVER',   null),
    ('22222222-2222-2222-2222-222222222222', 'permit@example.edu',  'Permit Holder', 'DRIVER',   date '2030-12-31'),
    ('33333333-3333-3333-3333-333333333333', 'operator@example.edu','Car Park Operator', 'OPERATOR', null);

insert into parking_spot (id, code, zone_code, type, status) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'P1-A01', 'P1', 'STANDARD',    'ACTIVE'),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'P1-A02', 'P1', 'STANDARD',    'ACTIVE'),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'P1-A03', 'P1', 'STANDARD',    'OUT_OF_SERVICE'),
    ('bbbbbbbb-0000-0000-0000-000000000001', 'P1-E01', 'P1', 'EV_CHARGING', 'ACTIVE'),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'P1-E02', 'P1', 'EV_CHARGING', 'ACTIVE'),
    ('cccccccc-0000-0000-0000-000000000001', 'P1-H01', 'P1', 'ACCESSIBLE',  'ACTIVE'),
    ('dddddddd-0000-0000-0000-000000000001', 'P1-M01', 'P1', 'MOTORCYCLE',  'ACTIVE');
