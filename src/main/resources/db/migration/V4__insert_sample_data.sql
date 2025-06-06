-- Insert roles
INSERT INTO roles (id, role_code, role_type, created_at) VALUES
(UUID(), 'ADMIN', 'SYSTEM_ROLE', CURRENT_TIMESTAMP),
(UUID(), 'MANAGER', 'SYSTEM_ROLE', CURRENT_TIMESTAMP),
(UUID(), 'USER', 'SYSTEM_ROLE', CURRENT_TIMESTAMP);

-- Insert users
INSERT INTO users (id, username, password, email, birthday, address, created_at) VALUES
(UUID(), 'ngoc', '$2a$10$xn3LI/AjqicFYZFruSwve.681477XaVNaUQbr1gioaWPn4t1KsnmG', 'ngoc@example.com', '1995-03-15', '123 Le Loi Street, District 1, Ho Chi Minh City', CURRENT_TIMESTAMP),
(UUID(), 'ngan', '$2a$10$xn3LI/AjqicFYZFruSwve.681477XaVNaUQbr1gioaWPn4t1KsnmG', 'ngan@example.com', '1997-08-22', '456 Nguyen Hue Street, District 1, Ho Chi Minh City', CURRENT_TIMESTAMP),
(UUID(), 'dat', '$2a$10$xn3LI/AjqicFYZFruSwve.681477XaVNaUQbr1gioaWPn4t1KsnmG', 'dat@example.com', '1994-11-30', '789 Tran Hung Dao Street, District 5, Ho Chi Minh City', CURRENT_TIMESTAMP),
(UUID(), 'phuong', '$2a$10$xn3LI/AjqicFYZFruSwve.681477XaVNaUQbr1gioaWPn4t1KsnmG', 'phuong@example.com', '1996-05-10', '321 Vo Van Tan Street, District 3, Ho Chi Minh City', CURRENT_TIMESTAMP);

-- Assign roles to users
-- ngoc as ADMIN
INSERT INTO user_role (user_id, role_id) 
SELECT u.id, r.id FROM users u, roles r 
WHERE u.username = 'ngoc' AND r.role_code = 'ADMIN';

-- ngan as MANAGER
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'ngan' AND r.role_code = 'MANAGER';

-- dat as USER
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'dat' AND r.role_code = 'USER';

-- phuong as USER
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'phuong' AND r.role_code = 'USER'; 