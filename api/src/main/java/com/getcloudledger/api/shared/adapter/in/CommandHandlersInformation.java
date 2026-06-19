package com.getcloudledger.api.shared.adapter.in;

import com.getcloudledger.api.shared.domain.bus.command.Command;
import com.getcloudledger.api.shared.domain.bus.command.CommandHandler;
import com.getcloudledger.api.shared.domain.bus.command.CommandNotRegisteredError;
import org.reflections.Reflections;
import org.springframework.stereotype.Service;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Set;

@Service
@SuppressWarnings("rawtypes")
public final class CommandHandlersInformation {
    private final HashMap<Class<? extends Command>, Class<? extends CommandHandler<?>>> indexedCommandHandlers;

    public CommandHandlersInformation() {
        Reflections reflections = new Reflections("com.getcloudledger.api");
        Set<Class<? extends CommandHandler>> classes = reflections.getSubTypesOf(CommandHandler.class);
        indexedCommandHandlers = formatHandlers(classes);
    }

    public Class<? extends CommandHandler<?>> search(Class<? extends Command> commandClass)
            throws CommandNotRegisteredError {
        Class<? extends CommandHandler<?>> handlerClass = indexedCommandHandlers.get(commandClass);
        if (handlerClass == null) {
            throw new CommandNotRegisteredError(commandClass);
        }
        return handlerClass;
    }

    @SuppressWarnings("unchecked")
    private HashMap<Class<? extends Command>, Class<? extends CommandHandler<?>>> formatHandlers(
            Set<Class<? extends CommandHandler>> commandHandlers) {
        var handlers = new HashMap<Class<? extends Command>, Class<? extends CommandHandler<?>>>();
        for (var handler : commandHandlers) {
            ParameterizedType paramType = (ParameterizedType) handler.getGenericInterfaces()[0];
            Class<? extends Command> commandClass = (Class<? extends Command>) paramType.getActualTypeArguments()[0];
            handlers.put(commandClass, (Class<? extends CommandHandler<?>>) handler);
        }
        return handlers;
    }
}
