from fastapi import FastAPI, HTTPException
import psycopg2
from psycopg2.extras import RealDictCursor
from passlib.context import CryptContext
from pydantic import BaseModel
from typing import List, Optional
import firebase_admin
from firebase_admin import credentials, messaging

app = FastAPI()
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

cred = credentials.Certificate("serviceAccountKey.json")
firebase_admin.initialize_app(cred)

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

def get_db_conn():
    return psycopg2.connect(
        host="db",
        database="ticket_db",
        user="admin",
        password="password123"
    )

@app.get("/")
def home():
    return {"status": "Server is running"}

@app.post("/register")
def register_user(user: UserRegister):
    conn = get_db_conn()
    cur = conn.cursor()
    try:
        clean_password = user.password.strip()
        if len(clean_password) > 72:
            raise HTTPException(status_code=400, detail="Password too long (max 72 chars)")
        if len(clean_password) < 6:
            raise HTTPException(status_code=400, detail="Password too short (min 6 chars)")

        cur.execute("SELECT id FROM users WHERE email = %s", (user.email,))
        if cur.fetchone():
            raise HTTPException(status_code=400, detail="Email already registered")

        hashed_password = pwd_context.hash(clean_password)
        
        cur.execute(
            "INSERT INTO users (name, email, password_hash) VALUES (%s, %s, %s) RETURNING id",
            (user.name, user.email, hashed_password)
        )
        user_id = cur.fetchone()[0]
        conn.commit()
        return {"id": user_id, "status": "success"}
    except HTTPException as he:
        raise he
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")
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
            raise HTTPException(status_code=401, detail="Invalid email or password")
        
        return {
            "id": db_user['id'],
            "name": db_user['name'],
            "status": "success"
        }
    except HTTPException as he:
        raise he
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
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
                "external_id": row.get("external_id"),
                "title": row["title"],
                "date_info": row.get("date_info"),
                "location": row.get("location"),
                "ticket_url": row.get("ticket_url"),
                "is_available": row.get("is_available", False),
                "category": {
                    "id": row["category_id"],
                    "name": row.get("cat_name"),
                    "icon_url": row.get("cat_icon")
                }
            })
        return results
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cur.close()
        conn.close()

@app.post("/subscribe")
def subscribe_to_event(sub: SubscriptionRequest):
    conn = get_db_conn()
    cur = conn.cursor()
    try:
        query = """
            INSERT INTO subscriptions (user_id, event_id) 
            VALUES (%s, %s) 
            ON CONFLICT DO NOTHING
        """
        cur.execute(query, (sub.user_id, sub.event_id))
        conn.commit()
        return {"status": "success"}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cur.close()
        conn.close()

@app.post("/send-test-push")
def send_test_push(token: str):
    try:
        message = messaging.Message(
            notification=messaging.Notification(
                title="Bilet Găsit! (din Python)",
                body="Serverul a detectat un bilet nou pe site.",
            ),
            token=token,
        )
        response = messaging.send(message)
        return {"status": "success", "message_id": response}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))