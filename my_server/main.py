from fastapi import FastAPI, HTTPException
import psycopg2
from psycopg2.extras import RealDictCursor
from passlib.context import CryptContext
from pydantic import BaseModel
from typing import List, Optional
import firebase_admin
from firebase_admin import credentials, messaging
import requests
from bs4 import BeautifulSoup
import asyncio
import sys

app = FastAPI()
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

cred = credentials.Certificate("serviceAccountKey.json")
firebase_admin.initialize_app(cred)

TICKET_KEYWORDS = [
    "choose seats", "alege locuri", "buy", "tickets", "bilete", "cumpără", 
    "check-out", "checkout", "select seats", "Add to cart", "add to cart", 
    "purchase", "finalize", "seat selection", "select your seats", 
    "get tickets", "proceed to checkout", "proceed to check-out"
]

class UserRegister(BaseModel):
    name: str
    email: str
    password: str

class UserLogin(BaseModel):
    email: str
    password: str

class SubscriptionRequest(BaseModel):
    user_id: int
    event_id: int

class TokenUpdate(BaseModel):
    user_id: int
    token: str

class CustomEventRequest(BaseModel):
    user_id: int
    title: str
    url: str
    date_info: str

def get_db_conn():
    return psycopg2.connect(
        host="db",
        database="ticket_db",
        user="admin",
        password="password123"
    )

async def monitor_tickets():
    await asyncio.sleep(5)
    while True:
        print("\n--- [SCRAPER] STARTING CHECK CYCLE ---", flush=True)
        conn = None
        try:
            conn = get_db_conn()
            cur = conn.cursor(cursor_factory=RealDictCursor)
            
            cur.execute("""
                SELECT DISTINCT e.id, e.ticket_url, e.title 
                FROM events e
                JOIN subscriptions s ON e.id = s.event_id
                WHERE e.is_available = FALSE
            """)
            events_to_check = cur.fetchall()
            print(f"[SCRAPER] Found {len(events_to_check)} events to monitor.", flush=True)

            headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}
            
            for event in events_to_check:
                try:
                    print(f"[SCRAPER] Checking: {event['title']}", flush=True)
                    res = requests.get(event['ticket_url'], headers=headers, timeout=10)
                    
                    if res.status_code == 200:
                        soup = BeautifulSoup(res.text, 'html.parser')
                        page_text = soup.get_text().lower()
                        found = any(word.lower() in page_text for word in TICKET_KEYWORDS)
                        
                        if found:
                            print(f"[SCRAPER] SUCCESS! Tickets found for {event['title']}. Updating DB...", flush=True)
                            
                            cur.execute("UPDATE events SET is_available = TRUE WHERE id = %s", (event['id'],))
                            conn.commit()

                            cur.execute("""
                                SELECT u.fcm_token FROM users u
                                JOIN subscriptions s ON u.id = s.user_id
                                WHERE s.event_id = %s AND u.fcm_token IS NOT NULL
                            """, (event['id'],))
                            
                            tokens = cur.fetchall()
                            for t in tokens:
                                try:
                                    message = messaging.Message(
                                        notification=messaging.Notification(
                                            title="Tickets Available!",
                                            body=f"Tickets for {event['title']} are now available!",
                                        ),
                                        token=t['fcm_token'],
                                    )
                                    messaging.send(message)
                                except Exception as push_err:
                                    print(f"[SCRAPER] Push failed: {push_err}")
                except Exception as e:
                    print(f"[SCRAPER] Error checking {event['title']}: {e}", flush=True)
            
            cur.close()
        except Exception as e:
            print(f"[SCRAPER] DATABASE ERROR: {e}", flush=True)
        finally:
            if conn:
                conn.close()
        
        print("--- [SCRAPER] CYCLE FINISHED. Sleeping 120s ---", flush=True)
        await asyncio.sleep(120)

@app.on_event("startup")
async def startup_event():
    print("DEBUG: Application startup - Launching Scraper Task", flush=True)
    asyncio.create_task(monitor_tickets())

@app.get("/")
def home():
    return {"status": "Server is running"}

@app.post("/register")
def register_user(user: UserRegister):
    conn = get_db_conn()
    cur = conn.cursor()
    try:
        clean_password = user.password.strip()
        hashed_password = pwd_context.hash(clean_password)
        cur.execute(
            "INSERT INTO users (name, email, password_hash) VALUES (%s, %s, %s) RETURNING id",
            (user.name, user.email, hashed_password)
        )
        user_id = cur.fetchone()[0]
        conn.commit()
        return {"id": user_id, "status": "success"}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cur.close()
        conn.close()

@app.post("/login")
def login_user(user: UserLogin):
    conn = get_db_conn()
    cur = conn.cursor(cursor_factory=RealDictCursor)
    try:
        cur.execute("SELECT * FROM users WHERE email = %s", (user.email,))
        db_user = cur.fetchone()
        if not db_user or not pwd_context.verify(user.password, db_user['password_hash']):
            raise HTTPException(status_code=401, detail="Invalid credentials")
        return {"id": db_user['id'], "name": db_user['name'], "status": "success"}
    finally:
        cur.close()
        conn.close()

@app.get("/events")
def get_events():
    conn = get_db_conn()
    cur = conn.cursor(cursor_factory=RealDictCursor)
    try:
        query = """
            SELECT e.*, c.name as cat_name, c.icon_url as cat_icon
            FROM events e
            LEFT JOIN categories c ON e.category_id = c.id
        """
        cur.execute(query)
        rows = cur.fetchall()
        results = []
        for row in rows:
            results.append({
                "id": row["id"],
                "title": row["title"],
                "date_info": row.get("date_info"),
                "location": row.get("location"),
                "ticket_url": row.get("ticket_url"),
                "is_available": row.get("is_available", False),
                "category": {
                    "id": row["category_id"],
                    "name": row["cat_name"] if row["cat_name"] else "Custom",
                    "icon_url": row.get("cat_icon")
                }
            })
        return results
    finally:
        cur.close()
        conn.close()

@app.post("/update-token")
def update_token(data: TokenUpdate):
    conn = get_db_conn()
    cur = conn.cursor()
    try:
        cur.execute("UPDATE users SET fcm_token = %s WHERE id = %s", (data.token, data.user_id))
        conn.commit()
        return {"status": "success"}
    finally:
        cur.close()
        conn.close()

@app.post("/subscribe")
def subscribe_to_event(sub: SubscriptionRequest):
    conn = get_db_conn()
    cur = conn.cursor()
    try:
        cur.execute("INSERT INTO subscriptions (user_id, event_id) VALUES (%s, %s) ON CONFLICT DO NOTHING", (sub.user_id, sub.event_id))
        cur.execute("UPDATE events SET is_available = FALSE WHERE id = %s", (sub.event_id,))
        conn.commit()
        return {"status": "success"}
    finally:
        cur.close()
        conn.close()

@app.post("/unsubscribe")
def unsubscribe_from_event(sub: SubscriptionRequest):
    conn = get_db_conn()
    cur = conn.cursor()
    try:
        cur.execute("DELETE FROM subscriptions WHERE user_id = %s AND event_id = %s", (sub.user_id, sub.event_id))
        cur.execute("UPDATE events SET is_available = FALSE WHERE id = %s", (sub.event_id,))
        conn.commit()
        return {"status": "success"}
    finally:
        cur.close()
        conn.close()

@app.get("/users/{user_id}/subscriptions")
def get_user_subscriptions(user_id: int):
    conn = get_db_conn()
    cur = conn.cursor(cursor_factory=RealDictCursor)
    try:
        cur.execute("SELECT event_id FROM subscriptions WHERE user_id = %s", (user_id,))
        rows = cur.fetchall()
        return [row['event_id'] for row in rows]
    finally:
        cur.close()
        conn.close()

@app.post("/events/custom")
def add_custom_event(data: CustomEventRequest):
    conn = get_db_conn()
    cur = conn.cursor()
    try:
        cur.execute("""
            INSERT INTO events (title, ticket_url, date_info, category_id) 
            VALUES (%s, %s, %s, (SELECT id FROM categories WHERE name = 'Custom' LIMIT 1))
            RETURNING id
        """, (data.title, data.url, data.date_info))
        event_id = cur.fetchone()[0]
        
        cur.execute("INSERT INTO subscriptions (user_id, event_id) VALUES (%s, %s)", (data.user_id, event_id))
        conn.commit()
        return {"status": "success"}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cur.close()
        conn.close()

@app.delete("/events/{event_id}")
def delete_event(event_id: int):
    conn = get_db_conn()
    cur = conn.cursor()
    try:
        cur.execute("DELETE FROM events WHERE id = %s", (event_id,))
        
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail="Event not found")
            
        conn.commit()
        return {"status": "success", "message": f"Event {event_id} deleted"}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cur.close()
        conn.close()