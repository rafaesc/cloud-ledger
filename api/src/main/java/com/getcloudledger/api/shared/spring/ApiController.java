package com.getcloudledger.api.shared.spring;

import com.getcloudledger.api.shared.domain.DomainError;
import com.getcloudledger.api.shared.domain.bus.command.Command;
import com.getcloudledger.api.shared.domain.bus.command.CommandBus;
import com.getcloudledger.api.shared.domain.bus.command.CommandHandlerExecutionError;
import com.getcloudledger.api.shared.domain.bus.query.Query;
import com.getcloudledger.api.shared.domain.bus.query.QueryBus;
import com.getcloudledger.api.shared.domain.bus.query.QueryHandlerExecutionError;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.HashMap;

@AllArgsConstructor
public abstract class ApiController {
    private final QueryBus queryBus;
    private final CommandBus commandBus;

    protected void dispatch(Command command) throws CommandHandlerExecutionError {
        commandBus.dispatch(command);
    }

    protected <R> R ask(Query query) throws QueryHandlerExecutionError {
        return queryBus.ask(query);
    }

    public abstract HashMap<Class<? extends DomainError>, HttpStatus> errorMapping();
}
