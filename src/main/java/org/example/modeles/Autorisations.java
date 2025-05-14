package org.example.modeles;

import jakarta.persistence.*;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "AUTORISATIONS") // correspond au nom exact de la table Oracle
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Autorisations {

    @Id
    @Column(name = "STAN") // Définit 'stan' comme la clé primaire
    private String stan;

    @Column(name = "PAN")
    private String pan;

    @Column(name = "CODE_TRAITEMENT")
    private String codeTraitement;

    @Column(name = "MONTANT")
    private String montant;

    @Column(name = "DATE_HEURE")
    private String dateHeure;

    @Column(name = "EXPIRATION")
    private String expiration;

    @Column(name = "TRACK2")
    private String track2;

    @Column(name = "REFERENCE")
    private String reference;

    @Column(name = "SOURCE_DB")
    private String source;
}
