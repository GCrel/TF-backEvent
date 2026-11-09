package org.gcrel.tfbackevent.report.exception;

import org.gcrel.tfbackevent.common.exception.ForbiddenException;

public class ReportAccessDeniedException extends ForbiddenException {
    public ReportAccessDeniedException() {
        super("No tenés permisos para ver los reportes de este evento.");
    }
}
