package com.getcloudledger.api.shared.adapter.in;

import com.getcloudledger.api.shared.domain.bus.query.Query;
import com.getcloudledger.api.shared.domain.bus.query.QueryBus;
import com.getcloudledger.api.shared.domain.bus.query.QueryHandler;
import com.getcloudledger.api.shared.domain.bus.query.QueryHandlerExecutionError;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings({"rawtypes", "unchecked"})
public class InMemoryQueryBus implements QueryBus {

    private final QueryHandlersInformation information;
    private final ApplicationContext context;

    public InMemoryQueryBus(QueryHandlersInformation information, ApplicationContext context) {
        this.information = information;
        this.context = context;
    }

    @Override
    public <R> R ask(Query query) throws QueryHandlerExecutionError {
        try {
            Class<? extends QueryHandler> handlerClass = information.search(query.getClass());
            QueryHandler handler = context.getBean(handlerClass);
            return (R) handler.handle(query);
        } catch (Throwable error) {
            throw new QueryHandlerExecutionError(error);
        }
    }
}
