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

TICKET_KEYWORDS = ["choose seats", "alege locuri", "buy", "tickets", "bilete", "check-out", "checkout", "select seats"]

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
                SELECT u.fcm_token, e.ticket_url, e.title 
                FROM subscriptions s
                JOIN users u ON s.user_id = u.id
                JOIN events e ON s.event_id = e.id
                WHERE u.fcm_token IS NOT NULL
            """)
            subs = cur.fetchall()
            print(f"[SCRAPER] Found {len(subs)} active subscriptions to check.", flush=True)

            headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}
            
            for sub in subs:
                try:
                    print(f"[SCRAPER] Checking URL for: {sub['title']}", flush=True)
                    res = requests.get(sub['ticket_url'], headers=headers, timeout=10)
                    
                    if res.status_code == 200:
                        soup = BeautifulSoup(res.text, 'html.parser')
                        page_text = soup.get_text().lower()
                        
                        found = any(word.lower() in page_text for word in TICKET_KEYWORDS)
                        
                        if found:
                            print(f"[SCRAPER] SUCCESS! Tickets found for {sub['title']}. Sending Push...", flush=True)
                            message = messaging.Message(
                                notification=messaging.Notification(
                                    title="Tickets Available!",
                                    body=f"Tickets for {sub['title']} are now available!",
                                ),
                                token=sub['fcm_token'],
                            )
                            messaging.send(message)
                        else:
                            print(f"[SCRAPER] No tickets yet for {sub['title']}.", flush=True)
                    else:
                        print(f"[SCRAPER] Warning: Site returned status {res.status_code} for {sub['title']}", flush=True)
                
                except Exception as e:
                    print(f"[SCRAPER] Error checking event {sub['title']}: {e}", flush=True)
            
            cur.close()
        except Exception as e:
            print(f"[SCRAPER] DATABASE ERROR: {e}", flush=True)
        finally:
            if conn:
                conn.close()
        
        print("--- [SCRAPER] CYCLE FINISHED. Next check in 120s ---\n", flush=True)
        await asyncio.sleep(120)

@app.on_event("startup")
async def startup_event():
    print("DEBUG: Application startup - Launching Background Scraper Task", flush=True)
    asyncio.create_task(monitor_tickets())

@app.get("/")
def home():
    return {"status": "Server is running"}

@app.post("/update-token")
def update_token(data: TokenUpdate):
    conn = get_db_conn()
    cur = conn.cursor()
    try:
        cur.execute("UPDATE users SET fcm_token = %s WHERE id = %s", (data.token, data.user_id))
        conn.commit()
        print(f"DEBUG: Token updated for user {data.user_id}", flush=True)
        return {"status": "success"}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cur.close()
        conn.close()

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
                    "name": row["cat_name"] if row["cat_name"] else "General",
                    "icon_url": row.get("cat_icon")
                }
            })
        return results
    except Exception as e:
        print(f"Error in get_events: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cur.close()
        conn.close()

@app.post("/subscribe")
def subscribe_to_event(sub: SubscriptionRequest):
    conn = get_db_conn()
    cur = conn.cursor()
    try:
        cur.execute("INSERT INTO subscriptions (user_id, event_id) VALUES (%s, %s) ON CONFLICT DO NOTHING", (sub.user_id, sub.event_id))
        conn.commit()
        print(f"DEBUG: User {sub.user_id} subscribed to event {sub.event_id}", flush=True)
        return {"status": "success"}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cur.close()
        conn.close()

@app.post("/unsubscribe")
def unsubscribe_from_event(sub: SubscriptionRequest):
    conn = get_db_conn()
    cur = conn.cursor()
    try:
        cur.execute(
            "DELETE FROM subscriptions WHERE user_id = %s AND event_id = %s", 
            (sub.user_id, sub.event_id)
        )
        conn.commit()
        return {"status": "success", "message": "Unsubscribed"}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cur.close()
        conn.close()

@app.get("/check-match")
def check_eventbook_tickets(url: str, token: str):
    try:
        headers = {"User-Agent": "Mozilla/5.0"}
        res = requests.get(url, headers=headers)
        soup = BeautifulSoup(res.text, 'html.parser')
        page_text = soup.get_text().lower()
        found = any(word.lower() in page_text for word in TICKET_KEYWORDS)
        if found:
            message = messaging.Message(
                notification=messaging.Notification(title="Tickets Found!", body="Manual check found tickets!"),
                token=token,
            )
            messaging.send(message)
            return {"status": "found"}
        return {"status": "not_found"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))