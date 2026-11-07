package com.FaceLit.backend.auth.service.serviceImpl.security;

import com.FaceLit.backend.auth.service.security.InitialCredentialsEmailRequested;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class InitialCredentialEmailListener {
    private final AccountAccessService access;
    public InitialCredentialEmailListener(AccountAccessService access) { this.access = access; }
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInitialCredentialsEmailRequested(InitialCredentialsEmailRequested event) {
        access.dispatch(event.requestId(), event.rawPassword());
    }
}
