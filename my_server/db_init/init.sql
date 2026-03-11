DROP TABLE IF EXISTS notification_history;
DROP TABLE IF EXISTS subscriptions;
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS categories;

CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    icon_url TEXT 
);

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    fcm_token TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE events (
    id SERIAL PRIMARY KEY,
    external_id VARCHAR(100) UNIQUE,
    title VARCHAR(255) NOT NULL,
    category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
    date_info VARCHAR(100),
    location VARCHAR(255),
    ticket_url TEXT,
    is_available BOOLEAN DEFAULT FALSE,
    last_checked TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
    last_change TIMESTAMP DEFAULT CURRENT_TIMESTAMP 
);

CREATE TABLE subscriptions (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    event_id INTEGER REFERENCES events(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, event_id)
);

CREATE TABLE notification_history (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    event_id INTEGER REFERENCES events(id) ON DELETE CASCADE,
    title VARCHAR(255),
    message TEXT,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN DEFAULT FALSE
);

INSERT INTO categories (name) VALUES ('Sports'), ('Concerts'), ('Festivals'), ('Theater');

-- Inseram date care respecta structura modelului Kotlin (inclusiv ticket_url)
INSERT INTO events (title, category_id, date_info, location, ticket_url, is_available) 
VALUES 
(
    'Untold Festival 2024', 
    (SELECT id FROM categories WHERE name = 'Festivals'), 
    'August 8-11', 
    'Cluj-Arena', 
    'https://untold.com/tickets', 
    true
),
(
    'Romania vs Ucraina', 
    (SELECT id FROM categories WHERE name = 'Sports'), 
    'Iunie 17', 
    'Munchen', 
    'https://tickets.uefa.com', 
    false
),
(
    'Coldplay Concert', 
    (SELECT id FROM categories WHERE name = 'Concerts'), 
    'Iunie 12', 
    'Arena Nationala', 
    'https://www.ticketmaster.com/coldplay', 
    true
),
(
    'Hamlet - Teatrul National', 
    (SELECT id FROM categories WHERE name = 'Theater'), 
    'Mai 20', 
    'Bucuresti', 
    'https://www.tnb.ro/bilete', 
    true
),
(
    'Saga Festival', 
    (SELECT id FROM categories WHERE name = 'Festivals'), 
    'Iulie 5-7', 
    'Romaero', 
    'https://sagafestival.com/tickets', 
    false
);