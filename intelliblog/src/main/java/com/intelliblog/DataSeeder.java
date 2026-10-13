package com.intelliblog;

import com.intelliblog.model.*;
import com.intelliblog.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Adds a demo account (demo / demo123) and sample posts the first time the app starts. */
@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final PostRepository posts;
    private final CommentRepository comments;

    public DataSeeder(UserRepository users, PostRepository posts, CommentRepository comments) {
        this.users = users; this.posts = posts; this.comments = comments;
    }

    @Override
    public void run(String... args) {
        if (users.findByUsername("demo").isPresent()) return;
        User u = new User();
        u.username = "demo";
        u.password = new BCryptPasswordEncoder().encode("demo123");
        u = users.save(u);

        String[][] d = {
            {"Getting Started with Spring Boot", "Tech", ",java,spring,backend,", "spring",
             "Spring Boot removes most of the setup pain from building Java back ends.\n\n## Why it is popular\n\n- Auto-configuration picks sensible defaults\n- An embedded server means you just run a jar\n- Starter dependencies keep your `pom.xml` short\n\nCreate a project with Spring Initializr, add the **Web** and **Data JPA** starters, and you can expose a REST endpoint in under ten lines."},
            {"Understanding JWT Authentication", "Security", ",jwt,security,java,", "lock",
             "A JSON Web Token is a signed string that proves who you are without the server storing a session.\n\n## How the flow works\n\n- You log in with a username and password\n- The server returns a signed token\n- You send it in the `Authorization` header on every request\n\nBecause the token is **signed**, the server can detect tampering. Keep tokens short-lived and never put secrets inside them."},
            {"10 Habits of Productive Developers", "Career", ",productivity,career,", "desk",
             "Productivity is mostly about small habits repeated daily.\n\n## A few that work\n\n- Plan the day before you open the editor\n- Write small commits with clear messages\n- Read other people's code every week\n- Take real breaks away from the screen\n\nPick **one** habit, practice it for two weeks, then add the next."},
            {"A Beginner's Guide to REST APIs", "Tech", ",rest,api,backend,", "api",
             "REST is a simple style for designing web APIs around resources and HTTP verbs.\n\n## The basics\n\n- `GET` reads data\n- `POST` creates data\n- `PUT` updates data\n- `DELETE` removes data\n\nUse nouns in URLs, return proper status codes, and always validate input on the server."},
            {"Learning Data Structures Efficiently", "Learning", ",dsa,learning,interview,", "code",
             "Memorizing solutions does not work; recognizing patterns does.\n\n## A practical plan\n\n- Learn arrays, hash maps, stacks and queues first\n- Solve problems by topic, not at random\n- Revisit hard problems after 3 and 7 days\n\nTrack your progress so you can see the improvement. Consistency beats long, rare sessions."},
            {"Using AI Tools to Learn Faster", "AI", ",ai,gemini,learning,", "robot",
             "AI assistants are great study partners when you use them to **understand**, not just to copy answers.\n\n## Ways to use them well\n\n- Ask for a summary, then check it against the source\n- Request hints instead of full solutions\n- Translate notes into your own language\n\nTry the AI tools on this page: summarize any post, translate it, or ask the chatbot a question."}
        };
        for (int i = 0; i < d.length; i++) {
            Post p = new Post();
            p.authorId = u.id; p.authorName = u.username;
            p.title = d[i][0]; p.category = d[i][1]; p.tags = d[i][2]; p.content = d[i][4];
            p.coverImage = "https://picsum.photos/seed/intelliblog-" + d[i][3] + "/900/500";
            p.status = "PUBLISHED";
            p.views = 40 + i * 57; p.likes = 3 + i * 4;
            p.createdAt = LocalDateTime.now().minusDays(i); p.publishedAt = p.createdAt;
            if (i == 0) p.commentCount = 2;
            p = posts.save(p);
            if (i == 0) {
                for (String[] c : new String[][]{{"alex", "Great intro, the starters tip saved me a lot of time."}, {"priya", "Would love a follow-up on Spring Data JPA."}}) {
                    Comment cm = new Comment();
                    cm.postId = p.id; cm.authorName = c[0]; cm.text = c[1];
                    comments.save(cm);
                }
            }
        }
    }
}
