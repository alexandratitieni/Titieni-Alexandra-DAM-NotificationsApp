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

INSERT INTO categories (name) VALUES ('Sports'), ('Concerts'), ('Festivals'), ('Theater'), ('Film');

INSERT INTO events (title, category_id, date_info, location, ticket_url, is_available) 
VALUES (
    'Teatru Variete la Iunion', 
    (SELECT id FROM categories WHERE name = 'Theater'), 
    'Aprilie 2026', 'Fabrica de Teatru', 
    'https://eventbook.ro/theater/bilete-variete-la-iunion?hall=fabrica-de-teatru', 
    false
),
(
    'Apolodor din Labrador', 
    (SELECT id FROM categories WHERE name = 'Theater'), 
    '26 Aprilie 2026', 'Fabrica de Teatru', 
    'https://eventbook.ro/theater/bilete-apolodor-din-labrador?hall=fabrica-de-teatru', 
    false
),
(
    'LIVE: MINDTHEGAP Club Control', 
    (SELECT id FROM categories WHERE name = 'Concerts'), 
    '29 Aprilie 2026', 'Club Control', 
    'https://eventbook.ro/music/bilete-ctrl-live-mindthegap-trio-ro?hall=club-control', 
    false
),
(
    'Abonament SWORDS Club Control', 
    (SELECT id FROM categories WHERE name = 'Concerts'), 
    '10 Mai 2026', 'Club Control', 
    'https://eventbook.ro/music/bilete-abonament-swords-season-4-control-club?hall=club-control', 
    false
),
(
    'Caravana Alpin Film Festival', 
    (SELECT id FROM categories WHERE name = 'Film'), 
    '19 Aprilie 2026', 'Bucuresti', 
    'https://eventbook.ro/film/bilete-alpin-film-festival-2026-abonament', 
    false
),
(
    'CSM TGM vs CSM CORONA BRASOV', 
    (SELECT id FROM categories WHERE name = 'Sports'), 
    '14 Aprilie 2026', 'Targu Mures', 
    'https://in-time.hu/e/csm-tgm-vs-csm-corona-brasov-1404', 
    false
),
(
    'Dinamo București vs CSM Oradea', 
    (SELECT id FROM categories WHERE name = 'Sports'), 
    '14 Aprilie 2026', 'Sala Polivalenta Dinamo', 
    'https://www.eventim.ro/event/clubul-sportiv-dinamo-baschet-masculin-sala-polivalenta-dinamo-21524176/', 
    false
);