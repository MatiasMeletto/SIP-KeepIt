package com.uade.tpo.keepit.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Ningún archivo incluido.")
public class ArchivoNotFileUploadedException extends Exception {

}
