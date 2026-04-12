# SE 333 — LLM Chatbot Starter  
**DePaul University · Spring 2026**

A minimal but production-shaped Spring Boot chatbot that talks to a free LLM
through [OpenRouter](https://openrouter.ai).  
Your job: extend it into something useful.

---

## Table of Contents

1. [What you get](#1-what-you-get)  
2. [Prerequisites](#2-prerequisites)  
3. [Get a free OpenRouter API key](#3-get-a-free-openrouter-api-key)  
4. [Configure the project](#4-configure-the-project)  
5. [Run the application](#5-run-the-application)  
6. [Test the API manually](#6-test-the-api-manually)  
7. [Code walkthrough](#7-code-walkthrough)  
8. [Extension exercises](#8-extension-exercises)  
9. [Troubleshooting](#9-troubleshooting)  

---

## 1. What you get

| Layer | File | Purpose |
|-------|------|---------|
| Entry point | `ChatbotApplication.java` | Boots Spring, creates `RestTemplate` |
| HTTP layer | `controller/ChatController.java` | REST endpoints |
| AI logic | `service/ChatService.java` | Calls OpenRouter, tracks history |
| Data shapes | `model/ChatMessage.java` | One conversation turn |
| | `model/ChatRequest.java` | JSON the browser sends |
| | `model/ChatResponse.java` | JSON sent back to browser |
| Config | `resources/application.properties` | API key, model, system prompt |
| UI | `resources/static/index.html` | Single-page chat interface |

```
POST  /api/chat           send { "message": "..." }, get { "reply": "..." }
DELETE /api/chat/history   wipe conversation memory
GET   /api/chat/history    inspect current history (debug)
```

---

## 2. Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java JDK | 21 or later | `java -version` |
| Maven | 3.9 or later | `mvn -version` |

> **Windows Tip:** install Java with [Adoptium](https://adoptium.net/) and
> Maven with [the official binary](https://maven.apache.org/download.cgi).
> Add both `bin/` directories to your `PATH`.

---

## 3. Get a free OpenRouter API key

1. Go to <https://openrouter.ai> and create a free account.  
2. Navigate to **Settings → API Keys → Create Key**.  
3. Copy the key — it looks like `sk-or-v1-xxxxxxxx…`.  

No credit card required for the free-tier models.

---

## 4. Configure the project

Open `src/main/resources/application.properties` and replace the placeholder:

```properties
openrouter.api.key=YOUR_OPENROUTER_API_KEY_HERE
```

The default model is `meta-llama/llama-3.1-8b-instruct:free`.  
Other free models you can switch to (just change one line):

```
mistralai/mistral-7b-instruct:free
google/gemma-3-27b-it:free
deepseek/deepseek-chat-v3-0324:free
```

> **Security reminder:** Do not commit your real API key to a public repo.
> The `.gitignore` already excludes `application-local.properties`; you may
> move your key there and keep `application.properties` key-free as a template.

---

## 5. Run the application

From the project root folder:

```bash
mvn spring-boot:run
```

Maven downloads dependencies on first run (~30 s).  
Once you see `Started ChatbotApplication`, open:

```
http://localhost:8080
```

You should see the chat UI.  
Stop the server with **Ctrl + C**.

To build a runnable JAR:

```bash
mvn package
java -jar target/chatbot-0.0.1-SNAPSHOT.jar
```

---

## Docker

Build a Docker image (multi-stage build produces a small runtime image):

```bash
# from project root
docker build -t se331-chatbot:latest .
```

Run the container locally, passing your OpenRouter API key as an environment variable:

```bash
docker run -p 8000:8000 -e OPENROUTER_API_KEY="$OPENROUTER_API_KEY" se331-chatbot:latest
```

The app will be available at `http://localhost:8000` inside your browser. Use `-e OPENROUTER_API_KEY=sk-or-...` to pass a key inline if you prefer (do not commit keys to source control).

---

## 6. Test the API manually

You can test without the UI using `curl` or any HTTP client
(PowerShell, Insomnia, Postman).

**Send a message:**
```bash
curl -X POST http://localhost:8080/api/chat \
     -H "Content-Type: application/json" \
     -d '{"message": "What is a Java interface?"}'
```

**Clear history:**
```bash
curl -X DELETE http://localhost:8080/api/chat/history
```

**Inspect history:**
```bash
curl http://localhost:8080/api/chat/history
```

---

## 7. Code walkthrough

### Request / response flow

1. Browser
    - POST /api/chat  { "message": "Hello" }
2. ChatController.chat()
    - calls chatService.chat(message)
3. ChatService.chat()
    - appends user message to history list
    - builds payload: system prompt + full history
    - POSTs to OpenRouter via RestTemplate
    - parses reply from JSON response
    - appends assistant reply to history list
    - returns reply string
4. ChatController
    - wraps reply in ChatResponse
5. Browser
    - {"reply": "Hello! How can I help?" }


### Key design decisions

- **In-memory history** — stored as a plain `ArrayList` in `ChatService`.
  Fast and simple; resets when the server restarts.

- **System prompt in config** — changing the chatbot's persona requires
  zero code changes: just edit `application.properties`.

- **Single-user** — all browser sessions share one history list.
  Fine for a demo; see Extension 2 for multi-user support.

- **OpenAI-compatible API** — OpenRouter uses the same JSON format as
  OpenAI, so your code works with many providers without modification.

---

## 8. Extension exercises

Start small. Each exercise is independent; you do not need to do them
in order. Moreover, some of these extensions are just for suggestions. 
You can decide to do complete different things.

---

### Exercise 1 — Give your chatbot a personality  
**Files to change:** `application.properties` only.

Change the system prompt to something specific:

```properties
chatbot.system.prompt=You are a strict Java tutor. \
  If the student's question is not about Java, \
  politely redirect them. Keep answers under 5 sentences.
```

Restart and test. Notice how the model's behaviour changes.

---

### Exercise 2 — Add a "summarize" endpoint  
**Files to change:** `ChatController.java`, `ChatService.java`.

Add a new REST endpoint that summarizes a block of text the student
pastes in — without storing it in the chat history.

Steps:
1. Add a new request model `SummarizeRequest` with a `text` field.
2. In `ChatService`, add a `summarize(String text)` method that sends
   a one-shot message (no history) to the LLM asking for a TL;DR.
3. In `ChatController`, add `POST /api/summarize`.

---

### Exercise 3 — Support multiple chat sessions  
**Files to change:** `ChatService.java`, `ChatController.java`,
`ChatRequest.java`.

The current code stores one shared history list. Replace it with a
`Map<String, List<ChatMessage>>` keyed by a session ID string.

Steps:
1. Add a `sessionId` field to `ChatRequest`.
2. Change `ChatService.chat()` to accept a `sessionId` parameter and
   look up / create the matching history list.
3. Add `DELETE /api/chat/history/{sessionId}` to clear one session.


---

### Exercise 4 — Persist history in a database  
**Files to change:** `pom.xml`, `ChatService.java`, new `Repository`.

Replace the in-memory list with a JPA repository backed by H2.

Steps:
1. Add `spring-boot-starter-data-jpa` and `h2` dependencies to `pom.xml`.
2. Annotate `ChatMessage` with `@Entity`.
3. Create a `ChatMessageRepository extends JpaRepository<...>`.
4. Replace the `ArrayList` in `ChatService` with repository calls.

Now history survives server restarts.


### Exercise 5 — This is your turn

You can extend this codebase to turn into any downstream application. 

Eventually, you would have to test this.


---

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `401 Unauthorized` | Wrong or missing API key | Re-check `application.properties` |
| `model not found` | Typo in model name | Copy exact name from openrouter.ai/models |
| Timeout after 60 s | Free model overloaded | Wait and retry, or switch models |
| Port 8000 in use | Another process | `server.port=9090` in `application.properties` |
| `Error: unexpected API response` | Check server log | Run with `mvn spring-boot:run`, look for stack trace |

For deeper debugging, add this to `application.properties` to see every
HTTP request/response in the console:

```properties
logging.level.org.springframework.web.client=DEBUG
```
