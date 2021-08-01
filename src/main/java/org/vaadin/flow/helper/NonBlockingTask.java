package org.vaadin.flow.helper;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.shared.communication.PushMode;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public class NonBlockingTask extends AsyncTask {

    private volatile Future<Void> vaadinFuture;

    /**
     * Create a new task
     *
     * @param asyncManager
     */
    NonBlockingTask(AsyncManager asyncManager) {
        super(asyncManager);
    }

    @Override
    public void push(Command command) {
        task = createFutureTask(command);
        execute();
    }

    private FutureTask<AsyncTask> createFutureTask(Command command) {
        return new FutureTask<>(() -> {
            if (task.isCancelled()) {
                return;
            }
            if (getUI() == null) {
                return;
            }
            boolean mustPush = missedPolls.get() == PUSH_ACTIVE && getUI().getPushConfiguration().getPushMode() == PushMode.MANUAL;
            vaadinFuture = getUI().access(() -> {
                try {
                    command.execute();
                    if (mustPush) {
                        getUI().push();
                    }
                } catch (UIDetachedException ignore) {
                    // Do not report
                    // How could this even happen?
                } catch (Exception e) {
                    // Dump
                    getAsyncManager().handleException(this, e);
                }
            });
        }, this);
    }

    @Override
    protected void registerPush(Component component, Action action) {
        add();
        missedPolls.set(PUSH_ACTIVE);
        try {
            action.run(this);
        } catch (Exception ex) {
            // Dump
            getAsyncManager().handleException(this, ex);
        }

        componentDetachListenerRegistration = component.addDetachListener(this::onDetachEvent);
        uiDetachListenerRegistration = getUI().addDetachListener(this::onDetachEvent);
        beforeLeaveListenerRegistration = getUI().addBeforeLeaveListener(this::onBeforeLeaveEvent);
    }

    @Override
    protected void registerPoll(Component component, Action action) {
        add();

        try {
            action.run(this);
        } catch (Exception ex) {
            // Dump
            getAsyncManager().handleException(this, ex);
        }

        pollingListenerRegistration = getUI().addPollListener(this::onPollEvent);

        uiDetachListenerRegistration = getUI().addDetachListener(this::onDetachEvent);
        componentDetachListenerRegistration = component.addDetachListener(this::onDetachEvent);
        beforeLeaveListenerRegistration = getUI().addBeforeLeaveListener(this::onBeforeLeaveEvent);

        getAsyncManager().adjustPollingInterval(getUI());
    }

    @Override
    public void cancel() {
        vaadinFuture.cancel(true);
        super.cancel();
    }
}
