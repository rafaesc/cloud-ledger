package com.getcloudledger.api.account.adapter.in.web;

import com.getcloudledger.api.account.adapter.in.web.request.TransferRequest;
import com.getcloudledger.api.account.application.transfer.TransferCommand;
import com.getcloudledger.api.shared.domain.DomainError;
import com.getcloudledger.api.shared.domain.bus.command.CommandBus;
import com.getcloudledger.api.shared.spring.ApiController;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@RestController
@RequestMapping("/v1/transfers")
public class TransferController extends ApiController {

    public TransferController(CommandBus commandBus) {
        super(null, commandBus);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void transfer(@RequestBody @Valid TransferRequest request) {
        dispatch(new TransferCommand(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                request.transferId()
        ));
    }

    @Override
    public HashMap<Class<? extends DomainError>, HttpStatus> errorMapping() {
        return new HashMap<>();
    }
}
