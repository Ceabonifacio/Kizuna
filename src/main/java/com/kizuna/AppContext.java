package com.kizuna;

import com.kizuna.db.Database;
import com.kizuna.db.DemoSeeder;
import com.kizuna.repository.ApplicationRepository;
import com.kizuna.repository.AssessmentRepository;
import com.kizuna.repository.ClubRepository;
import com.kizuna.repository.EventRepository;
import com.kizuna.repository.MembershipRepository;
import com.kizuna.repository.UserRepository;
import com.kizuna.service.ApplicationService;
import com.kizuna.service.AssessmentService;
import com.kizuna.service.AuthService;
import com.kizuna.service.AuthorizationService;
import com.kizuna.service.EventService;
import com.kizuna.service.MembershipService;
import com.kizuna.service.RecommendationEngine;
import com.kizuna.service.Session;
import com.kizuna.util.AvatarStore;
import com.kizuna.util.PasswordHasher;

import java.nio.file.Path;

public final class AppContext {
    public final Path dataDirectory;
    public final Database database;
    public final AvatarStore avatars;
    public final Session session;
    public final AuthService auth;
    public final AssessmentService assessment;
    public final MembershipService memberships;
    public final ApplicationService applications;
    public final EventService events;

    private AppContext(Path dataDirectory, Database database, AvatarStore avatars, Session session,
                       AuthService auth, AssessmentService assessment, MembershipService memberships,
                       ApplicationService applications, EventService events) {
        this.dataDirectory = dataDirectory; this.database = database; this.avatars = avatars; this.session = session;
        this.auth = auth; this.assessment = assessment; this.memberships = memberships;
        this.applications = applications; this.events = events;
    }

    public static AppContext create() throws Exception {
        Path dataDirectory = Path.of(System.getProperty("user.home"), ".kizuna").toAbsolutePath().normalize();
        Database database = new Database(dataDirectory.resolve("kizuna.db"));
        database.initialize();
        PasswordHasher hasher = new PasswordHasher();
        database.transaction(connection -> { new DemoSeeder(hasher).seed(connection); return null; });

        AvatarStore avatars = new AvatarStore(dataDirectory.resolve("avatars"));
        Session session = new Session();
        UserRepository users = new UserRepository();
        ClubRepository clubs = new ClubRepository();
        MembershipRepository membershipRepository = new MembershipRepository(clubs);
        AuthorizationService authorization = new AuthorizationService(session, membershipRepository);
        AuthService auth = new AuthService(database, users, hasher, avatars, session);
        AssessmentService assessment = new AssessmentService(database, session, new AssessmentRepository(), users, new RecommendationEngine());
        MembershipService memberships = new MembershipService(database, session, authorization, membershipRepository);
        ApplicationService applications = new ApplicationService(database, session, authorization, membershipRepository, new ApplicationRepository());
        EventService events = new EventService(database, session, authorization, new EventRepository());
        return new AppContext(dataDirectory, database, avatars, session, auth, assessment, memberships, applications, events);
    }
}
