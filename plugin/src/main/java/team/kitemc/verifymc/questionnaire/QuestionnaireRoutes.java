package team.kitemc.verifymc.questionnaire;

import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiConsumer;

public class QuestionnaireRoutes {
    private final HttpHandler questionnaireConfigHandler;
    private final HttpHandler questionnaireSubmitHandler;

    public QuestionnaireRoutes(
            HttpHandler questionnaireConfigHandler,
            HttpHandler questionnaireSubmitHandler
    ) {
        this.questionnaireConfigHandler = questionnaireConfigHandler;
        this.questionnaireSubmitHandler = questionnaireSubmitHandler;
    }

    public void register(BiConsumer<String, HttpHandler> registerApiRoute) {
        registerApiRoute.accept("/api/questionnaire/config", questionnaireConfigHandler);
        registerApiRoute.accept("/api/questionnaire/submit", questionnaireSubmitHandler);
    }
}
