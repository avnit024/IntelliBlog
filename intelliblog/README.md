# IntelliBlog - Blog Management Platform

Spring Boot REST API + HTML/CSS/JavaScript frontend, JWT authentication, Gemini AI.

## Features
- Register / login with BCrypt + JWT (stateless authentication and authorization)
- Create, edit, delete, publish / unpublish posts (authors can only touch their own posts)
- Public reading site with search, category filter, tag filter and pagination
- Author dashboard: totals, views, posts by category, top posts, search/filter/pagination of own posts
- Gemini AI: post summarization, translation (5 languages), floating chatbot that knows the post you are reading

## What is new in v2
- **Sample blogs** and a demo account are created on first start: log in with `demo` / `demo123`
- **Cover images:** paste an image URL or upload a file (png/jpg/gif/webp, max 5MB, saved in `./uploads`)
- **Likes** (one per user), **comments**, reading time, related posts, copy-link button
- **Sorting** (latest / most viewed / most liked), **Markdown** posts (headings, lists, bold, code), **dark mode**
- Dockerfile and GitHub Actions workflow included

## Run in VS Code
Needs JDK 17+ and Maven (`mvn -v`), plus the "Extension Pack for Java".
1. Open this folder (the one containing `pom.xml`) in VS Code.
2. Run `IntelliBlogApplication.java` (Run button above `main`) or `mvn spring-boot:run`.
3. Open http://localhost:8081, register, then go to the Dashboard and write a post.

**Enable AI features (easiest):** log in, click the **✨ Set up AI** pill in the header, paste your free key from https://aistudio.google.com/apikey and press *Save and test*. The key is saved in `data/gemini.key` on your computer.
Other ways: copy `local.properties.example` to `local.properties` and paste the key, or set the environment variable below.

**Enable AI features (environment variable)**: get a free key from Google AI Studio, set it before starting:
- Mac/Linux: `export GEMINI_API_KEY=your-key`
- Windows PowerShell: `$env:GEMINI_API_KEY="your-key"`

## API
| Method | URL | Auth | Description |
|---|---|---|---|
| POST | /api/auth/register, /api/auth/login | no | create account / get JWT |
| GET | /api/public/posts?search=&category=&tag=&page=&size= | no | published posts (paginated) |
| GET | /api/public/posts/{id} | no | read a post (counts a view) |
| GET | /api/public/meta | no | categories and tags with counts |
| GET, POST | /api/posts | JWT | my posts (search, status, page) / create |
| GET, PUT, DELETE | /api/posts/{id} | JWT | read / update / delete my post |
| POST | /api/posts/{id}/publish | JWT | toggle draft / published |
| GET | /api/analytics | JWT | dashboard analytics |
| GET | /api/public/posts/{id}/comments | no | list comments |
| POST | /api/posts/{id}/comments | JWT | add a comment |
| GET, POST | /api/posts/{id}/like | JWT | liked? / toggle like |
| POST | /api/upload | JWT | upload a cover image |
| POST | /api/ai/summarize, /translate, /chat | JWT | Gemini features |

Test with Postman: log in, copy the token, send it as `Authorization: Bearer <token>`.
The H2 database is stored in `./data`. Set `JWT_SECRET` to use your own signing key.

**Port:** IntelliBlog runs on 8081 so it does not clash with other projects on 8080. Change `server.port` in `application.properties` if needed.

## v2.1
Professional header (search, active links, Write button, AI status pill, profile menu), new colour palette, featured post, loading skeletons,
reading-progress bar. AI: in-app key setup with test, automatic model fallback, clear error messages, chatbot memory,
and editor tools (suggest tags, improve writing). Note: any logged-in user can change the AI key, so this setup is meant for a personal, local install.

## v2.3 Daily news
Headlines from public RSS feeds (BBC, NPR, Al Jazeera, TechCrunch, Ars Technica, Wired, The Hindu) are imported automatically 5 seconds after start-up and then every 3 hours,
shown under "Today's headlines" on the home page and as normal posts (categories World, Technology, Business, Science, India).
Only the publisher's short summary is stored, with the source name and a link to the full story. Posts older than 30 days (with no likes or comments) are removed.
Change feeds, how many per feed, or the interval in `application.properties` (`app.news.*`). The "AI briefing" button turns the headlines into 5 bullet points with Gemini.
Needs an internet connection. If a feed is down it is skipped and the others still load.
