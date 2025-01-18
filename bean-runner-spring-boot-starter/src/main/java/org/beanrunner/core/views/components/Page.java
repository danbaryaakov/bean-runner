package org.beanrunner.core.views.components;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;

import java.util.*;
import java.util.function.Consumer;

public class Page extends VerticalLayout  implements HasUrlParameter<String>, BeforeLeaveObserver {

    private Map<Integer, Consumer<String>> parameterConsumers = new HashMap<>();
    private Map<String, Consumer<String>> queryParamConsumers = new HashMap<>();
    private List<String> initialPathParameters = new ArrayList<>();
    private List<String> pathParameters = new ArrayList<>();
    private Map<String, String> queryParameters = new HashMap<>();

    private Runnable onParametersSet;

    private boolean parametersSet = false;
    private boolean hasChanges;

    public Page() {
        setSizeFull();
        setMargin(false);
        setPadding(false);
    }

    @Override
    public void setParameter(BeforeEvent event, @WildcardParameter String parameter) {
        if (parametersSet) {
            return;
        }

        String[] params = parameter.split("/");
        pathParameters = new ArrayList<>();
        Collections.addAll(pathParameters, params);
        Collections.addAll(initialPathParameters, params);

        QueryParameters queryParams = event.getLocation().getQueryParameters();
        queryParams.getParameters().forEach((key, value) -> {
            Consumer<String> queryParamConsumer = queryParamConsumers.get(key);
            if (! value.isEmpty() && queryParamConsumer != null) {
                queryParamConsumer.accept(value.get(0));
            }
        });

        parametersSet = true;

        if (onParametersSet != null) {
            onParametersSet.run();
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        for (int i = 0; i < initialPathParameters.size(); i++) {
            if (parameterConsumers.containsKey(i)) {
                parameterConsumers.get(i).accept(initialPathParameters.get(i));
            } else {
                return;
            }
        }
    }

    protected void setPathParameter(int index, String value) {
        List<String> newParams = new ArrayList<>(pathParameters.subList(0, Math.min(index, pathParameters.size())));
        newParams.add(index, value);
        pathParameters = newParams;
        String deepLinkUrl = RouteConfiguration.forSessionScope().getUrl(getClass(), pathParameters);
        getUI().ifPresent(ui -> ui.getPage().getHistory().replaceState(null, deepLinkUrl));
    }

    public void setUnsavedChanges(boolean unsavedChanges) {
        hasChanges = unsavedChanges;
        if (unsavedChanges) {
            getUI().ifPresent(ui -> ui.getPage().executeJs("window.setUnsavedChanges();"));
        } else {
            getUI().ifPresent(ui -> ui.getPage().executeJs("window.resetUnsavedChanges();"));
        }
    }

    public void onPathParameter(int index, Consumer<String> consumer) {
        parameterConsumers.put(index, consumer);
    }

    public void onQueryParameter(String key, Consumer<String> consumer) {
        queryParamConsumers.put(key, consumer);
    }

    public void onParametersSet(Runnable runnable) {
        this.onParametersSet = runnable;
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        if (hasChanges) {
            BeforeLeaveEvent.ContinueNavigationAction action =
                    event.postpone();
            ConfirmDialog confirmDialog = new ConfirmDialog();
            confirmDialog.setText("Are you sure you want to leave?");
            confirmDialog.setCancelable(true);
            confirmDialog.addConfirmListener(__ -> action.proceed());
            confirmDialog.addCancelListener(__ -> action.cancel());
            confirmDialog.open();
        }
    }

}