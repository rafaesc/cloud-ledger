package com.getcloudledger.api.shared.adapter.in;

import com.getcloudledger.api.shared.domain.bus.command.Command;
import com.getcloudledger.api.shared.domain.bus.command.CommandBus;
import com.getcloudledger.api.shared.domain.bus.command.CommandHandler;
import com.getcloudledger.api.shared.domain.bus.command.CommandHandlerExecutionError;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings({"rawtypes", "unchecked"})
public class InMemoryCommandBus implements CommandBus {

    private final CommandHandlersInformation information;
    private final ApplicationContext context;

    public InMemoryCommandBus(CommandHandlersInformation information, ApplicationContext context) {
        this.information = information;
        this.context = context;
    }

    @Override
    public void dispatch(Command command) throws CommandHandlerExecutionError {
        try {
            Class<? extends CommandHandler> handlerClass = information.search(command.getClass());
            CommandHandler handler = context.getBean(handlerClass);
            handler.handle(command);
        } catch (Throwable error) {
            throw new CommandHandlerExecutionError(error);
        }
    }
}
