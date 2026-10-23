package com.kizuna.ui;

import com.kizuna.AppContext;
import com.kizuna.model.Models.AdminApplication;
import com.kizuna.model.Models.ApplicationStatus;
import com.kizuna.model.Models.ApplicationSummary;
import com.kizuna.model.Models.AssessmentResult;
import com.kizuna.model.Models.Club;
import com.kizuna.model.Models.Event;
import com.kizuna.model.Models.EventForm;
import com.kizuna.model.Models.MemberProfile;
import com.kizuna.model.Models.NetworkClub;
import com.kizuna.model.Models.Question;
import com.kizuna.model.Models.Recommendation;
import com.kizuna.model.Models.Role;
import com.kizuna.model.Models.UserProfile;
import com.kizuna.util.TimeUtil;
import com.kizuna.util.SvgLogoRenderer;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class MainView {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' h:mm a", Locale.ENGLISH);
    private final AppContext context;
    private final Stage stage;
    private final BorderPane root = new BorderPane();
    private long selectedManageClub;

    public MainView(AppContext context, Stage stage) {
        this.context = context; this.stage = stage;
        root.getStyleClass().add("app-root");
        refreshHeader();
        showLogin();
    }

    public BorderPane root() { return root; }

    private void refreshHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("top-bar");
        Label brand = new Label("Kizuna"); brand.getStyleClass().add("brand");
        Label tagline = new Label("Campus Event and Student Club Network App"); tagline.getStyleClass().add("top-tagline");
        Label school = new Label("Mindanao Kokusai Daigaku"); school.getStyleClass().add("school-name");
        VBox identity = new VBox(2, brand, tagline, school);
        VBox logoBadge = new VBox(logoView(46)); logoBadge.setAlignment(Pos.CENTER); logoBadge.getStyleClass().add("brand-logo-frame");
        HBox branding = new HBox(12, logoBadge, identity); branding.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(branding, spacer);
        context.session.currentUser().ifPresent(user -> {
            Label signedIn = new Label(user.fullName()); signedIn.getStyleClass().add("top-user");
            Button logout = button("Log out", "button-secondary", () -> { context.auth.logout(); refreshHeader(); showLogin(); });
            header.getChildren().addAll(signedIn, logout);
        });
        root.setTop(header);
    }

    private void setContent(Node node) { root.setCenter(node); }

    private VBox page(String title, String description) {
        VBox box = new VBox(22);
        box.setPadding(new Insets(32, 36, 42, 36));
        box.setFillWidth(true);
        Label heading = new Label(title); heading.getStyleClass().add("page-title"); heading.setWrapText(true);
        box.getChildren().add(heading);
        if (description != null && !description.isBlank()) {
            Label summary = new Label(description); summary.getStyleClass().add("page-description"); summary.setWrapText(true);
            box.getChildren().add(summary);
        }
        return box;
    }

    private ScrollPane scroll(Node node) {
        ScrollPane scroll = new ScrollPane(node);
        scroll.setFitToWidth(true); scroll.setPannable(true); scroll.getStyleClass().add("content-scroll");
        return scroll;
    }

    private VBox card() { VBox box = new VBox(12); box.setPadding(new Insets(20)); box.getStyleClass().add("card"); return box; }
    private Label label(String text) { Label label = new Label(text); label.setWrapText(true); return label; }
    private Label sectionTitle(String text) { Label label = label(text); label.getStyleClass().add("section-title"); return label; }
    private Button button(String text, String style, Runnable action) {
        Button button = new Button(text); button.getStyleClass().add(style); button.setMinHeight(42);
        button.setOnAction(event -> action.run()); return button;
    }
    private VBox field(String title, Node control) {
        Label label = label(title); label.getStyleClass().add("field-label");
        label.setLabelFor(control);
        VBox group = new VBox(8, label, control); group.setFillWidth(true); return group;
    }
    private Label messageLabel() { Label label = new Label(); label.setWrapText(true); label.getStyleClass().add("form-message"); return label; }
    private void error(Label label, Throwable error) {
        label.getStyleClass().removeAll("message-success"); label.getStyleClass().add("message-error");
        String text = error.getMessage(); label.setText(text == null || text.isBlank() ? "That action could not be completed. Please try again." : text);
    }
    private void success(Label label, String text) {
        label.getStyleClass().removeAll("message-error"); label.getStyleClass().add("message-success"); label.setText(text);
    }

    private void showLogin() {
        refreshHeader();
        VBox page = page("Welcome back", "Sign in to explore clubs, events, and student connections.");
        TextField username = new TextField(); username.setPromptText("Username");
        PasswordField password = new PasswordField(); password.setPromptText("Password");
        Label message = messageLabel();
        Button[] loginRef = new Button[1];
        loginRef[0] = button("Log in", "button-primary", () -> {
            String submittedUsername = username.getText();
            char[] secret = password.getText().toCharArray();
            loginRef[0].setDisable(true); message.setText("Signing in…");
            Task<UserProfile> task = new Task<>() { @Override protected UserProfile call() { return context.auth.login(submittedUsername, secret); } };
            task.setOnSucceeded(event -> afterLogin(task.getValue()));
            task.setOnFailed(event -> { loginRef[0].setDisable(false); password.clear(); error(message, task.getException()); });
            startTask(task, "kizuna-login");
        });
        Button login = loginRef[0];
        Button signup = button("Create an account", "button-link", this::showSignup);
        VBox form = card(); form.setSpacing(16); form.setPadding(new Insets(24)); form.setMaxWidth(480); form.setAlignment(Pos.TOP_CENTER);
        form.getChildren().addAll(logoView(124), sectionTitle("Student sign in"), field("Username", username), field("Password", password), message, login, signup);
        page.setAlignment(Pos.TOP_CENTER); page.getChildren().add(form);
        setContent(scroll(page));
    }

    private void showSignup() {
        VBox page = page("Create your account", "Sign up as a student. Club leadership is assigned only to trusted seeded accounts.");
        TextField username = new TextField();
        TextField fullName = new TextField();
        ComboBox<String> year = new ComboBox<>(); year.getItems().addAll("1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year", "Other"); year.getSelectionModel().selectFirst();
        PasswordField password = new PasswordField(); PasswordField confirm = new PasswordField();
        Label photoName = label("No photo selected");
        final Path[] photo = new Path[1];
        Button choosePhoto = button("Choose photo", "button-secondary", () -> {
            FileChooser chooser = new FileChooser(); chooser.setTitle("Choose profile photo");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JPG or PNG images", "*.jpg", "*.jpeg", "*.png"));
            File selected = chooser.showOpenDialog(stage);
            if (selected == null) return;
            photo[0] = selected.toPath(); photoName.setText(selected.getName());
        });
        HBox photoRow = new HBox(12, new VBox(8, photoName, choosePhoto)); photoRow.setAlignment(Pos.CENTER_LEFT);
        Label message = messageLabel();
        Button[] createRef = new Button[1];
        createRef[0] = button("Create account", "button-primary", () -> {
            char[] secret = password.getText().toCharArray(); char[] repeated = confirm.getText().toCharArray();
            if (!Arrays.equals(secret, repeated)) {
                Arrays.fill(secret, '\0'); Arrays.fill(repeated, '\0'); password.clear(); confirm.clear();
                error(message, new IllegalArgumentException("Passwords do not match.")); return;
            }
            if (secret.length < 10 || secret.length > 128) {
                Arrays.fill(secret, '\0'); Arrays.fill(repeated, '\0'); password.clear(); confirm.clear();
                error(message, new IllegalArgumentException("Use a password between 10 and 128 characters.")); return;
            }
            Arrays.fill(repeated, '\0');
            String submittedUsername = username.getText(); String submittedName = fullName.getText(); String selectedYear = year.getValue(); Path selectedPhoto = photo[0];
            createRef[0].setDisable(true); message.setText("Creating your account…");
            Task<UserProfile> task = new Task<>() { @Override protected UserProfile call() { return context.auth.signup(submittedUsername, secret, submittedName, selectedYear, selectedPhoto); } };
            task.setOnSucceeded(event -> afterLogin(task.getValue()));
            task.setOnFailed(event -> { createRef[0].setDisable(false); password.clear(); confirm.clear(); error(message, task.getException()); });
            password.clear(); confirm.clear(); startTask(task, "kizuna-signup");
        });
        Button create = createRef[0];
        Button back = button("Back to log in", "button-link", this::showLogin);
        VBox form = card(); form.setSpacing(16); form.setPadding(new Insets(24)); form.setMaxWidth(560);
        form.setAlignment(Pos.TOP_CENTER);
        form.getChildren().addAll(logoView(116), sectionTitle("Student account"), field("Username", username), field("Full name", fullName), field("Academic year", year),
                field("Password (10–128 characters)", password), field("Confirm password", confirm), field("Profile photo (optional)", photoRow), message, create, back);
        page.setAlignment(Pos.TOP_CENTER); page.getChildren().add(form); setContent(scroll(page));
    }

    private void afterLogin(UserProfile profile) {
        refreshHeader();
        if (profile.assessmentCompletedAt() == null && context.memberships.administeredClubs().isEmpty()) showAssessment();
        else showDashboard();
    }

    private void startTask(Task<?> task, String name) { Thread thread = new Thread(task, name); thread.setDaemon(true); thread.start(); }

    private void showDashboard() {
        UserProfile user = context.session.currentUser().orElse(null);
        if (user == null) { showLogin(); return; }
        List<Club> managed = context.memberships.administeredClubs();
        if (user.assessmentCompletedAt() == null && managed.isEmpty()) { showAssessment(); return; }
        refreshHeader();
        VBox page = page("Hello, " + user.fullName(), "Find a club community, see what is happening, and follow your applications.");
        FlowPane actions = new FlowPane(16, 16);
        actions.getChildren().addAll(dashboardAction("See Club Network", "Explore active club memberships as a network or hierarchy.", this::showNetwork),
                dashboardAction("Upcoming Club Events", "Browse upcoming events from all six clubs.", this::showUpcomingEvents),
                dashboardAction("My Applications", "Check application and interview progress.", this::showMyApplications));
        if (managed.isEmpty()) actions.getChildren().add(dashboardAction("Preference Assessment", "View or retake your club preference assessment.", this::showAssessment));
        page.getChildren().addAll(sectionTitle("Explore Kizuna"), actions);
        if (!managed.isEmpty()) page.getChildren().add(dashboardAction("Manage My Clubs", "Review applications, members, and events for your clubs.", this::showManageClubs));

        List<Recommendation> recommendations = context.assessment.recommendations();
        page.getChildren().add(sectionTitle("Club recommendations"));
        if (recommendations.isEmpty()) page.getChildren().add(emptyState("No eligible recommendations are available yet. Retake the assessment or review the club network."));
        else for (Recommendation recommendation : recommendations) {
            VBox item = card();
            HBox heading = new HBox(10); heading.setAlignment(Pos.CENTER_LEFT);
            Label name = sectionTitle(recommendation.clubName()); Label score = new Label("Match score " + recommendation.score()); score.getStyleClass().add("score-pill");
            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS); heading.getChildren().addAll(name, spacer, score);
            item.getChildren().addAll(heading, label(recommendation.reason()), label(recommendation.description()), button("Apply to " + recommendation.clubName(), "button-primary", () -> showApply(recommendation.clubId())));
            page.getChildren().add(item);
        }
        setContent(scroll(page));
    }

    private VBox dashboardAction(String title, String description, Runnable action) {
        VBox box = card(); box.setPrefWidth(295);
        Button open = button("Open", "button-secondary", action);
        open.setAccessibleText(title + ", open");
        box.getChildren().addAll(sectionTitle(title), label(description), open); return box;
    }

    private ImageView logoView(double size) {
        ImageView logo = new ImageView(SvgLogoRenderer.logo());
        logo.setPreserveRatio(true);
        logo.setFitWidth(size);
        logo.setFitHeight(size);
        logo.setAccessibleText("Kizuna logo");
        return logo;
    }

    private Node emptyState(String text) { VBox box = card(); box.getStyleClass().add("empty-state"); box.getChildren().add(label(text)); return box; }

    private void showAssessment() {
        refreshHeader();
        List<Question> questions = context.assessment.questions();
        VBox page = page("Your club preferences", "Choose one answer for each question. Your answers are stored privately and used to suggest clubs.");
        Map<Long, ToggleGroup> groups = new LinkedHashMap<>();
        for (Question question : questions) {
            VBox qCard = card(); qCard.getChildren().add(sectionTitle(question.prompt()));
            ToggleGroup group = new ToggleGroup(); groups.put(question.id(), group);
            for (var option : question.options()) {
                RadioButton radio = new RadioButton(option.label()); radio.setToggleGroup(group); radio.setUserData(option.id()); radio.setWrapText(true); radio.setMinHeight(38); qCard.getChildren().add(radio);
            }
            page.getChildren().add(qCard);
        }
        Label message = messageLabel();
        Button submit = button("Save preferences", "button-primary", () -> {
            Map<Long, Long> answers = new HashMap<>();
            for (Map.Entry<Long, ToggleGroup> entry : groups.entrySet()) {
                Toggle selected = entry.getValue().getSelectedToggle();
                if (selected == null) { error(message, new IllegalArgumentException("Answer every question before submitting.")); return; }
                answers.put(entry.getKey(), ((Number) selected.getUserData()).longValue());
            }
            try { AssessmentResult result = context.assessment.submit(answers); showAssessmentResults(result); }
            catch (RuntimeException e) { error(message, e); }
        });
        page.getChildren().addAll(message, submit);
        if (context.session.currentUser().map(UserProfile::assessmentCompletedAt).orElse(null) != null) {
            page.getChildren().add(button("Back to dashboard", "button-link", this::showDashboard));
        }
        setContent(scroll(page));
    }

    private void showAssessmentResults(AssessmentResult result) {
        VBox page = page("Your club matches", "These scores come from interests you selected. Clubs you already joined or applied to are left out of this list.");
        page.getChildren().add(label("Scores saved for " + result.clubsScored() + " clubs."));
        if (result.recommendations().isEmpty()) page.getChildren().add(emptyState("No eligible club recommendations remain. You can still browse the network and events."));
        for (Recommendation recommendation : result.recommendations()) {
            VBox item = card(); item.getChildren().addAll(sectionTitle(recommendation.clubName()), label("Match score: " + recommendation.score()), label(recommendation.reason()), label(recommendation.description()),
                    button("Apply", "button-primary", () -> showApply(recommendation.clubId())));
            page.getChildren().add(item);
        }
        page.getChildren().add(button("Go to dashboard", "button-secondary", this::showDashboard)); setContent(scroll(page));
    }

    private void showNetwork() {
        List<NetworkClub> network = context.memberships.network();
        VBox page = page("Club Network", "View active club memberships. Pending applicants and inactive memberships are not shown.");
        ComboBox<String> filter = new ComboBox<>(); filter.getItems().add("All clubs");
        network.forEach(item -> filter.getItems().add(item.club().name())); filter.getSelectionModel().selectFirst();
        ToggleGroup viewGroup = new ToggleGroup(); RadioButton graph = new RadioButton("Network Graph"); RadioButton tree = new RadioButton("Club Hierarchy");
        graph.setToggleGroup(viewGroup); tree.setToggleGroup(viewGroup); graph.setSelected(true);
        HBox controls = new HBox(18, field("Club filter", filter), graph, tree); controls.setAlignment(Pos.BOTTOM_LEFT);
        StackPane content = new StackPane();
        Runnable redraw = () -> {
            String selected = filter.getValue();
            List<NetworkClub> chosen = selected == null || selected.equals("All clubs") ? network : network.stream().filter(item -> item.club().name().equals(selected)).toList();
            if (tree.isSelected()) {
                if (chosen.size() != 1) content.getChildren().setAll(emptyState("Choose one club to see its hierarchy."));
                else content.getChildren().setAll(hierarchyPane(chosen.getFirst()));
            } else content.getChildren().setAll(networkPane(chosen));
        };
        filter.setOnAction(event -> redraw.run()); viewGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> redraw.run());
        page.getChildren().addAll(controls, legend(), content, button("Back to dashboard", "button-secondary", this::showDashboard));
        redraw.run(); setContent(scroll(page));
    }

    private Node legend() {
        HBox legend = new HBox(18, label("Role appears on each student card"));
        legend.getStyleClass().add("legend"); return legend;
    }

    private Node networkPane(List<NetworkClub> clubs) {
        if (clubs.isEmpty()) return emptyState("No clubs are available.");
        double contentHeight = 24;
        for (NetworkClub club : clubs) contentHeight += Math.max(190, 20 + club.members().size() * 170.0);
        Pane pane = new Pane(); pane.setPrefSize(1120, Math.max(160, contentHeight + 10));
        double y = 24;
        for (int i = 0; i < clubs.size(); i++) {
            NetworkClub item = clubs.get(i);
            double rowHeight = Math.max(190, 20 + item.members().size() * 170.0);
            VBox clubCard = card(); clubCard.getStyleClass().add("club-node"); clubCard.setPrefSize(300, 160); clubCard.setLayoutX(24); clubCard.setLayoutY(y + 20);
            Label clubName = sectionTitle(item.club().name()); clubName.setMaxWidth(Double.MAX_VALUE);
            Label description = label(item.club().description()); description.setMaxWidth(Double.MAX_VALUE);
            clubCard.getChildren().addAll(clubName, description);
            pane.getChildren().add(clubCard);
            if (item.members().isEmpty()) {
                Label none = label("No active members"); none.getStyleClass().add("muted"); none.setLayoutX(410); none.setLayoutY(y + 91); pane.getChildren().add(none);
                y += rowHeight;
                continue;
            }
            double spineX = 360;
            double firstCenterY = y + 100;
            double lastCenterY = firstCenterY + (item.members().size() - 1) * 170.0;
            Line clubEdge = new Line(324, firstCenterY, spineX, firstCenterY); clubEdge.getStyleClass().add("graph-edge"); pane.getChildren().add(clubEdge);
            Line spine = new Line(spineX, firstCenterY, spineX, lastCenterY);
            spine.getStyleClass().add("graph-edge"); pane.getChildren().add(spine);
            for (int j = 0; j < item.members().size(); j++) {
                double x = 410;
                double nodeY = y + 20 + j * 170.0;
                double centerY = nodeY + 80;
                Line branch = new Line(spineX, centerY, x, centerY); branch.getStyleClass().add("graph-edge"); pane.getChildren().add(branch);
                VBox node = memberCard(item.members().get(j)); node.setPrefSize(330, 160); node.setMinSize(330, 160);
                node.setLayoutX(x); node.setLayoutY(nodeY); pane.getChildren().add(node);
            }
            y += rowHeight;
        }
        ScrollPane scroll = new ScrollPane(pane); scroll.setFitToHeight(false); scroll.setFitToWidth(false); scroll.setPannable(true); scroll.setPrefViewportHeight(470); scroll.getStyleClass().add("graph-scroll");
        Button scrollDown = button("Scroll down", "button-link", () -> scroll.setVvalue(1));
        scrollDown.setAccessibleHelp("Move to the bottom of the network graph.");
        HBox scrollAction = new HBox(scrollDown); scrollAction.setAlignment(Pos.CENTER_RIGHT);
        return new VBox(6, scroll, scrollAction);
    }

    private Node hierarchyPane(NetworkClub network) {
        Map<Role, List<MemberProfile>> groups = new LinkedHashMap<>();
        groups.put(Role.PRESIDENT, new ArrayList<>()); groups.put(Role.VICE_PRESIDENT, new ArrayList<>()); groups.put(Role.MEMBER, new ArrayList<>());
        for (MemberProfile member : network.members()) groups.get(member.role()).add(member);
        int maxLeaves = groups.values().stream().mapToInt(List::size).max().orElse(1);
        double height = Math.max(650, 340 + maxLeaves * 180.0);
        Pane pane = new Pane(); pane.setPrefSize(1180, height);
        VBox rootCard = card(); rootCard.getStyleClass().add("club-node"); rootCard.setAlignment(Pos.CENTER); rootCard.setPrefSize(400, 150); rootCard.setLayoutX(390); rootCard.setLayoutY(20);
        Label clubName = sectionTitle(network.club().name()); clubName.setMaxWidth(Double.MAX_VALUE);
        Label description = label(network.club().description()); description.setMaxWidth(Double.MAX_VALUE);
        rootCard.getChildren().addAll(clubName, description); pane.getChildren().add(rootCard);
        Role[] roles = {Role.PRESIDENT, Role.VICE_PRESIDENT, Role.MEMBER};
        String[] names = {"President", "Vice President", "Members"}; double[] x = {30, 430, 830};
        double[] centers = {190, 590, 990};
        Line trunk = new Line(590, 170, 590, 195); trunk.getStyleClass().add("hierarchy-edge"); pane.getChildren().add(trunk);
        Line level = new Line(centers[0], 195, centers[2], 195); level.getStyleClass().add("hierarchy-edge"); pane.getChildren().add(level);
        for (int i = 0; i < roles.length; i++) {
            List<MemberProfile> people = groups.get(roles[i]); double center = centers[i];
            Line rootEdge = new Line(center, 195, center, 225); rootEdge.getStyleClass().add("hierarchy-edge"); pane.getChildren().add(rootEdge);
            VBox group = card(); group.getStyleClass().add("group-node"); group.setAlignment(Pos.CENTER); group.setPrefSize(320, 60); group.setLayoutX(x[i]); group.setLayoutY(225);
            group.getChildren().add(sectionTitle(names[i])); pane.getChildren().add(group);
            if (people.isEmpty()) {
                Label none = label("No active members"); none.getStyleClass().add("muted"); none.setLayoutX(x[i] + 35); none.setLayoutY(310); pane.getChildren().add(none);
                continue;
            }
            double spineX = x[i] + 18;
            double firstCenterY = 405;
            double lastCenterY = firstCenterY + (people.size() - 1) * 180.0;
            double branchY = 305;
            Line groupDrop = new Line(center, 285, center, branchY); groupDrop.getStyleClass().add("hierarchy-edge"); pane.getChildren().add(groupDrop);
            Line branchFromGroup = new Line(center, branchY, spineX, branchY); branchFromGroup.getStyleClass().add("hierarchy-edge"); pane.getChildren().add(branchFromGroup);
            Line spine = new Line(spineX, branchY, spineX, lastCenterY); spine.getStyleClass().add("hierarchy-edge"); pane.getChildren().add(spine);
            for (int j = 0; j < people.size(); j++) {
                double y = 325 + j * 180.0;
                Line edge = new Line(spineX, y + 80, x[i] + 35, y + 80); edge.getStyleClass().add("hierarchy-edge"); pane.getChildren().add(edge);
                VBox person = memberCard(people.get(j)); person.setPrefSize(275, 160); person.setMinSize(275, 160);
                person.setLayoutX(x[i] + 35); person.setLayoutY(y); pane.getChildren().add(person);
            }
        }
        ScrollPane scroll = new ScrollPane(pane); scroll.setPrefViewportHeight(500); scroll.setFitToWidth(false); scroll.setPannable(true); scroll.getStyleClass().add("graph-scroll"); return scroll;
    }

    private VBox memberCard(MemberProfile member) {
        VBox details = new VBox(4, label(member.fullName()), label(member.academicYear()), label(roleName(member.role())));
        details.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(details, Priority.ALWAYS);
        HBox row = new HBox(10, avatar(member), details);
        row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("member-node"); row.setPadding(new Insets(10));
        row.setMaxHeight(Double.MAX_VALUE); VBox.setVgrow(row, Priority.ALWAYS);
        row.setAccessibleText(member.fullName() + ", " + member.academicYear() + ", " + roleName(member.role()));
        return new VBox(row);
    }

    private Node avatar(MemberProfile member) {
        StackPane holder = new StackPane(); holder.setPrefSize(46, 46); holder.setMinSize(46, 46);
        Circle background = new Circle(23, Color.web("#DCEBE9")); Label initials = new Label(initials(member.fullName())); initials.getStyleClass().add("initials"); holder.getChildren().addAll(background, initials);
        Path photo = context.avatars.resolve(member.photoFilename());
        if (photo != null) {
            try {
                Image image = new Image(photo.toUri().toString(), 46, 46, true, true);
                if (!image.isError()) {
                    ImageView view = new ImageView(image); view.setFitWidth(46); view.setFitHeight(46); view.setPreserveRatio(false); view.setClip(new Circle(23, 23, 23));
                    holder.getChildren().setAll(view);
                }
            } catch (RuntimeException ignored) { }
        }
        holder.setAccessibleText("Profile photo or initials for " + member.fullName()); return holder;
    }

    private static String initials(String name) {
        String[] words = name.trim().split("\\s+");
        if (words.length == 1) return words[0].substring(0, 1).toUpperCase(Locale.ROOT);
        return (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }
    private static String roleName(Role role) { return switch (role) { case PRESIDENT -> "President"; case VICE_PRESIDENT -> "Vice President"; case MEMBER -> "Member"; }; }

    private void showApply(long clubId) {
        Club club;
        try { club = context.database.read(c -> new com.kizuna.repository.ClubRepository().find(c, clubId)); }
        catch (Exception e) { club = null; }
        if (club == null) { showDashboard(); return; }
        VBox page = page("Apply to " + club.name(), club.description());
        TextArea motivation = new TextArea(); motivation.setPromptText("What would you like to contribute?"); motivation.setPrefRowCount(4); motivation.setWrapText(true);
        TextArea experience = new TextArea(); experience.setPromptText("Relevant experience (optional)"); experience.setPrefRowCount(3); experience.setWrapText(true);
        TextArea availability = new TextArea(); availability.setPromptText("Availability notes (optional)"); availability.setPrefRowCount(3); availability.setWrapText(true);
        Label message = messageLabel();
        Button submit = button("Submit application", "button-primary", () -> {
            try { context.applications.apply(clubId, motivation.getText(), experience.getText(), availability.getText()); showMyApplications(); }
            catch (RuntimeException e) { error(message, e); }
        });
        page.getChildren().addAll(cardWithFields("Application details", field("Why would you like to join?", motivation), field("Experience (optional)", experience), field("Availability (optional)", availability)), message, submit,
                button("Cancel", "button-link", this::showDashboard));
        setContent(scroll(page));
    }

    private VBox cardWithFields(String title, Node... fields) { VBox box = card(); box.getChildren().add(sectionTitle(title)); box.getChildren().addAll(fields); return box; }

    private void showMyApplications() {
        VBox page = page("My Applications", "Your application history and interview details are visible only to you and the club administrators reviewing them.");
        List<ApplicationSummary> applications = context.applications.myApplications();
        if (applications.isEmpty()) page.getChildren().add(emptyState("You have not submitted any club applications."));
        for (ApplicationSummary application : applications) {
            VBox item = card(); item.getChildren().addAll(sectionTitle(application.clubName()), statusBadge(application.status()), label("Submitted " + formatTime(application.submittedAt())));
            if (application.interviewAt() != null) item.getChildren().addAll(label("Interview: " + formatTime(application.interviewAt())), label("Location: " + application.interviewLocation()));
            if (application.status() == ApplicationStatus.INTERVIEW_SCHEDULED) {
                item.getChildren().add(label("This interview is waiting for your confirmation."));
                item.getChildren().add(button("Confirm interview time", "button-primary", () -> {
                    try { context.applications.confirmInterview(application.id()); showMyApplications(); }
                    catch (RuntimeException e) { showInlineError(item, e); }
                }));
            } else if (application.status() == ApplicationStatus.INTERVIEW_CONFIRMED) item.getChildren().add(label("Your interview time is confirmed."));
            page.getChildren().add(item);
        }
        page.getChildren().addAll(button("Browse clubs", "button-secondary", this::showNetwork), button("Dashboard", "button-link", this::showDashboard));
        setContent(scroll(page));
    }

    private Node statusBadge(ApplicationStatus status) {
        Label badge = label(statusText(status)); badge.getStyleClass().add("status-badge");
        badge.getStyleClass().add("status-" + status.name().toLowerCase(Locale.ROOT)); return badge;
    }
    private static String statusText(ApplicationStatus status) { return status.name().replace('_', ' '); }

    private void showUpcomingEvents() {
        VBox page = page("Upcoming Club Events", "Upcoming events from across the club network, ordered by start time.");
        List<Event> events = context.events.upcoming();
        if (events.isEmpty()) page.getChildren().add(emptyState("There are no upcoming events right now. Check back later."));
        for (Event event : events) {
            VBox item = card(); item.getChildren().addAll(sectionTitle(event.title()), label(event.clubName() + " · " + formatTime(event.startsAt()) + " to " + formatTime(event.endsAt())), label("Location: " + event.location()), label(event.description()));
            page.getChildren().add(item);
        }
        page.getChildren().add(button("Dashboard", "button-link", this::showDashboard)); setContent(scroll(page));
    }

    private static String formatTime(long epochMillis) { return DATE_TIME.format(TimeUtil.fromEpochMillis(epochMillis)); }

    private void showInlineError(VBox parent, Throwable error) { Label message = messageLabel(); error(message, error); parent.getChildren().add(message); }

    private boolean confirm(String title, String body) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION); alert.setTitle(title); alert.setHeaderText(title); alert.setContentText(body);
        return alert.showAndWait().filter(button -> button.getButtonData().isDefaultButton()).isPresent();
    }

    private void showManageClubs() {
        refreshHeader();
        List<Club> clubs = context.memberships.administeredClubs();
        VBox page = page("Manage My Clubs", "These tools are available only for clubs where your active membership is President or Vice President.");
        if (clubs.isEmpty()) {
            page.getChildren().addAll(emptyState("You do not currently administer any clubs."), button("Dashboard", "button-link", this::showDashboard));
            setContent(scroll(page)); return;
        }
        if (clubs.stream().noneMatch(club -> club.id() == selectedManageClub)) selectedManageClub = clubs.getFirst().id();
        ComboBox<Club> clubPicker = new ComboBox<>(); clubPicker.getItems().setAll(clubs);
        clubPicker.setConverter(new StringConverter<>() {
            @Override public String toString(Club club) { return club == null ? "" : club.name(); }
            @Override public Club fromString(String string) { return null; }
        });
        clubPicker.getSelectionModel().select(clubs.stream().filter(club -> club.id() == selectedManageClub).findFirst().orElse(clubs.getFirst()));
        Tab applications = new Tab("Applications"); applications.setClosable(false);
        Tab members = new Tab("Members"); members.setClosable(false);
        Tab events = new Tab("Events"); events.setClosable(false);
        TabPane tabs = new TabPane(applications, members, events); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Runnable update = () -> {
            Club selected = clubPicker.getValue(); if (selected == null) return;
            selectedManageClub = selected.id();
            applications.setContent(adminApplicationsPane(selected.id()));
            members.setContent(adminMembersPane(selected.id()));
            events.setContent(adminEventsPane(selected.id()));
        };
        clubPicker.setOnAction(event -> update.run()); update.run();
        page.getChildren().addAll(field("Club you administer", clubPicker), tabs, button("Dashboard", "button-link", this::showDashboard));
        setContent(scroll(page));
    }

    private Node adminApplicationsPane(long clubId) {
        VBox list = new VBox(14); list.setPadding(new Insets(16));
        List<AdminApplication> applications;
        try { applications = context.applications.applicationsForClub(clubId); }
        catch (RuntimeException e) { list.getChildren().add(emptyState(e.getMessage())); return list; }
        if (applications.isEmpty()) list.getChildren().add(emptyState("No applications have been submitted to this club."));
        for (AdminApplication application : applications) {
            VBox item = card();
            item.getChildren().addAll(sectionTitle(application.applicantName()), statusBadge(application.status()),
                    label("Submitted " + formatTime(application.submittedAt())),
                    label("Motivation: " + application.motivation()),
                    label("Experience: " + displayOptional(application.experience())),
                    label("Availability: " + displayOptional(application.availability())));
            if (application.interviewAt() != null) item.getChildren().add(label("Interview: " + formatTime(application.interviewAt()) + " · " + application.interviewLocation()));
            boolean schedulable = application.status() == ApplicationStatus.PENDING || application.status() == ApplicationStatus.INTERVIEW_SCHEDULED;
            DatePicker date = new DatePicker(LocalDate.now().plusDays(2));
            TextField time = new TextField("10:00"); TextField location = new TextField(application.interviewLocation() == null ? "" : application.interviewLocation());
            HBox scheduleFields = new HBox(10, field("Interview date", date), field("Time (24-hour HH:mm)", time), field("Location", location));
            scheduleFields.setAlignment(Pos.BOTTOM_LEFT);
            Button schedule = button(application.status() == ApplicationStatus.INTERVIEW_SCHEDULED ? "Reschedule interview" : "Propose interview", "button-secondary", () -> {
                try {
                    LocalDateTime local = LocalDateTime.of(date.getValue(), LocalTime.parse(time.getText().trim()));
                    context.applications.scheduleInterview(application.id(), TimeUtil.toEpochMillis(local), location.getText());
                    showManageClubs();
                } catch (RuntimeException e) { showInlineError(item, e); }
            });
            schedule.setDisable(!schedulable);
            if (!schedulable) schedule.setAccessibleHelp("Scheduling is available while an application is pending or already scheduled.");
            HBox decisions = new HBox(10);
            Button accept = button("Accept", "button-primary", () -> {
                try { context.applications.accept(application.id()); showManageClubs(); }
                catch (RuntimeException e) { showInlineError(item, e); }
            });
            accept.setDisable(application.status() != ApplicationStatus.INTERVIEW_CONFIRMED);
            if (accept.isDisabled()) accept.setAccessibleHelp("Accept after the applicant confirms the interview.");
            Button reject = button("Reject", "button-danger", () -> {
                if (!confirm("Reject application", "Reject " + application.applicantName() + "'s application?")) return;
                try { context.applications.reject(application.id()); showManageClubs(); }
                catch (RuntimeException e) { showInlineError(item, e); }
            });
            reject.setDisable(application.status() == ApplicationStatus.ACCEPTED || application.status() == ApplicationStatus.REJECTED);
            if (reject.isDisabled()) reject.setAccessibleHelp("This application has already been reviewed.");
            decisions.getChildren().addAll(accept, reject);
            item.getChildren().addAll(scheduleFields, schedule, decisions);
            if (!schedulable) item.getChildren().add(label("Interview scheduling is available while an application is pending or already scheduled."));
            if (application.status() == ApplicationStatus.PENDING || application.status() == ApplicationStatus.INTERVIEW_SCHEDULED)
                item.getChildren().add(label("Accept is available after the applicant confirms an interview."));
            if (application.status() == ApplicationStatus.ACCEPTED || application.status() == ApplicationStatus.REJECTED)
                item.getChildren().add(label("This application has already reached a final status."));
            if (application.status() == ApplicationStatus.INTERVIEW_SCHEDULED) item.getChildren().add(label("Waiting for the applicant to confirm the proposed time."));
            if (application.status() == ApplicationStatus.INTERVIEW_CONFIRMED) item.getChildren().add(label("The applicant confirmed this interview. You can now accept or reject."));
            list.getChildren().add(item);
        }
        return scroll(list);
    }

    private static String displayOptional(String text) { return text == null || text.isBlank() ? "Not provided" : text; }

    private Node adminMembersPane(long clubId) {
        VBox list = new VBox(12); list.setPadding(new Insets(16));
        List<MemberProfile> members;
        try { members = context.memberships.activeMembers(clubId); }
        catch (RuntimeException e) { list.getChildren().add(emptyState(e.getMessage())); return list; }
        if (members.isEmpty()) list.getChildren().add(emptyState("No active members are listed for this club."));
        for (MemberProfile member : members) {
            VBox item = memberCard(member);
            if (member.role() == Role.MEMBER) {
                Button remove = button("Remove member", "button-danger", () -> {
                    if (!confirm("Remove member", "Remove " + member.fullName() + " from this club? Their account and other memberships remain.")) return;
                    try { context.memberships.removeMember(clubId, member.userId()); showManageClubs(); }
                    catch (RuntimeException e) { showInlineError(item, e); }
                });
                item.getChildren().add(remove);
            } else {
                Label protectedRole = label("Leadership memberships cannot be removed here."); protectedRole.getStyleClass().add("muted"); item.getChildren().add(protectedRole);
            }
            list.getChildren().add(item);
        }
        return scroll(list);
    }

    private Node adminEventsPane(long clubId) {
        VBox body = new VBox(16); body.setPadding(new Insets(16));
        VBox form = card(); form.getChildren().add(sectionTitle("Create or edit event"));
        TextField title = new TextField(); TextArea description = new TextArea(); description.setPrefRowCount(3); description.setWrapText(true);
        TextField location = new TextField(); DatePicker startDate = new DatePicker(LocalDate.now().plusDays(1));
        TextField startTime = new TextField("10:00"); DatePicker endDate = new DatePicker(LocalDate.now().plusDays(1)); TextField endTime = new TextField("11:00");
        HBox times = new HBox(10, field("Start date", startDate), field("Start time", startTime), field("End date", endDate), field("End time", endTime));
        Label formMessage = messageLabel(); long[] editing = {0};
        Runnable[] refresh = {() -> { }};
        Button[] saveRef = new Button[1];
        saveRef[0] = button("Create event", "button-primary", () -> {
            try {
                long starts = TimeUtil.toEpochMillis(LocalDateTime.of(startDate.getValue(), LocalTime.parse(startTime.getText().trim())));
                long ends = TimeUtil.toEpochMillis(LocalDateTime.of(endDate.getValue(), LocalTime.parse(endTime.getText().trim())));
                EventForm eventForm = new EventForm(clubId, title.getText(), description.getText(), location.getText(), starts, ends);
                if (editing[0] == 0) context.events.create(eventForm); else context.events.edit(editing[0], eventForm);
                success(formMessage, editing[0] == 0 ? "Event created." : "Event updated.");
                editing[0] = 0; saveRef[0].setText("Create event"); title.clear(); description.clear(); location.clear();
                startDate.setValue(LocalDate.now().plusDays(1)); startTime.setText("10:00"); endDate.setValue(LocalDate.now().plusDays(1)); endTime.setText("11:00");
                refresh[0].run();
            } catch (RuntimeException e) { error(formMessage, e); }
        });
        Button save = saveRef[0];
        Button clear = button("Clear form", "button-link", () -> { editing[0] = 0; saveRef[0].setText("Create event"); title.clear(); description.clear(); location.clear(); formMessage.setText(""); });
        form.getChildren().addAll(field("Title", title), field("Description", description), field("Location", location), times, formMessage, new HBox(10, save, clear));
        VBox listings = new VBox(12); listings.getChildren().add(sectionTitle("Club events"));
        refresh[0] = () -> {
            listings.getChildren().removeIf(node -> node != listings.getChildren().getFirst());
            List<Event> events;
            try { events = context.events.forClub(clubId); }
            catch (RuntimeException e) { listings.getChildren().add(emptyState(e.getMessage())); return; }
            if (events.isEmpty()) listings.getChildren().add(emptyState("No events have been created for this club."));
            for (Event event : events) {
                VBox item = card(); item.getChildren().addAll(sectionTitle(event.title()), label(event.active() ? "Active" : "Inactive"), label(formatTime(event.startsAt()) + " · " + event.location()), label(event.description()));
                HBox controls = new HBox(8);
                if (event.active()) {
                    controls.getChildren().add(button("Edit", "button-secondary", () -> {
                        editing[0] = event.id(); saveRef[0].setText("Save changes"); title.setText(event.title()); description.setText(event.description()); location.setText(event.location());
                        LocalDateTime start = TimeUtil.fromEpochMillis(event.startsAt()); LocalDateTime end = TimeUtil.fromEpochMillis(event.endsAt());
                        startDate.setValue(start.toLocalDate()); startTime.setText(start.toLocalTime().toString()); endDate.setValue(end.toLocalDate()); endTime.setText(end.toLocalTime().toString());
                    }));
                    controls.getChildren().add(button("Deactivate", "button-danger", () -> {
                        if (!confirm("Deactivate event", "Hide “" + event.title() + "” from upcoming events?")) return;
                        try { context.events.deactivate(event.id()); refresh[0].run(); }
                        catch (RuntimeException e) { showInlineError(item, e); }
                    }));
                }
                item.getChildren().add(controls); listings.getChildren().add(item);
            }
        };
        refresh[0].run(); body.getChildren().addAll(form, listings); return scroll(body);
    }

    private void showInlineError(Node parent, Throwable throwable) {
        Label message = messageLabel(); error(message, throwable);
        if (parent instanceof VBox box) box.getChildren().add(message);
    }
}

