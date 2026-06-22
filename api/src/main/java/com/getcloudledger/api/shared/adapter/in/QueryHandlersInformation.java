package com.getcloudledger.api.shared.adapter.in;

import com.getcloudledger.api.shared.domain.bus.query.Query;
import com.getcloudledger.api.shared.domain.bus.query.QueryHandler;
import com.getcloudledger.api.shared.domain.bus.query.QueryNotRegisteredError;
import org.reflections.Reflections;
import org.springframework.stereotype.Service;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Set;

@Service
@SuppressWarnings("rawtypes")
public final class QueryHandlersInformation {
    private final HashMap<Class<? extends Query>, Class<? extends QueryHandler<?, ?>>> indexedQueryHandlers;

    public QueryHandlersInformation() {
        Reflections reflections = new Reflections("com.getcloudledger.api");
        Set<Class<? extends QueryHandler>> classes = reflections.getSubTypesOf(QueryHandler.class);
        indexedQueryHandlers = formatHandlers(classes);
    }

    public Class<? extends QueryHandler<?, ?>> search(Class<? extends Query> queryClass)
            throws QueryNotRegisteredError {
        Class<? extends QueryHandler<?, ?>> handlerClass = indexedQueryHandlers.get(queryClass);
        if (handlerClass == null) {
            throw new QueryNotRegisteredError(queryClass);
        }
        return handlerClass;
    }

    @SuppressWarnings("unchecked")
    private HashMap<Class<? extends Query>, Class<? extends QueryHandler<?, ?>>> formatHandlers(
            Set<Class<? extends QueryHandler>> queryHandlers) {
        var handlers = new HashMap<Class<? extends Query>, Class<? extends QueryHandler<?, ?>>>();
        for (var handler : queryHandlers) {
            ParameterizedType paramType = (ParameterizedType) handler.getGenericInterfaces()[0];
            Class<? extends Query> queryClass = (Class<? extends Query>) paramType.getActualTypeArguments()[0];
            handlers.put(queryClass, (Class<? extends QueryHandler<?, ?>>) handler);
        }
        return handlers;
    }
}
