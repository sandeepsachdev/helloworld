# Hello World - Spring Boot

A simple Spring Boot app that returns "Hello, World!" over HTTP.

## Endpoints

- `GET /` → `Hello, World!`
- `GET /health` → `OK`

## Deploy to Render (Free)

### Step 1: Push to GitHub
```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git push -u origin main
```

### Step 2: Create a Render Web Service
1. Go to https://render.com and sign up (free, no credit card)
2. Click **New → Web Service**
3. Connect your GitHub account and select this repo
4. Use these settings:
   - **Environment:** Docker
   - **Branch:** main
   - **Instance Type:** Free
5. Click **Create Web Service**

Render will build the Docker image and deploy it. Your app will be live at:
`https://your-app-name.onrender.com`

> ⚠️ Free tier on Render spins down after 15 min of inactivity. First request after sleep may take ~30 seconds.

## Run Locally

```bash
./mvnw spring-boot:run
# or
mvn spring-boot:run
```

Then visit http://localhost:8080
