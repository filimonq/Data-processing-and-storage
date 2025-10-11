package ru.nsu.fitkulin.DTO;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

public record ClientKey(PrivateKey privateKey, PublicKey publicKey, X509Certificate certificate) {
}
