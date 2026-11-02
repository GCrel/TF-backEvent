package org.gcrel.tfbackevent.event.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class DeferredImageDeletion {
    private DeferredImageDeletion() {
    }

    // Los archivos se borran recién al cerrar la transacción: si se confirma sobra
    // la imagen anterior, y si se revierte sobra la nueva. Así ninguna entidad
    // apunta a un archivo inexistente. Requiere una transacción activa.
    public static void afterCompletion(ImageStorageService storage, String keyIfCommitted, String keyIfRolledBack) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                storage.delete(status == STATUS_COMMITTED ? keyIfCommitted : keyIfRolledBack);
            }
        });
    }
}
