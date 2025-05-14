package org.example.services;

import org.example.modeles.Autorisations;
import org.example.repository.AutorisationsRepository;
import org.springframework.stereotype.Service;

@Service
public class AutorisationsService {

    private final AutorisationsRepository autorisationsRepository;

    public AutorisationsService(AutorisationsRepository autorisationsRepository) {
        this.autorisationsRepository = autorisationsRepository;
    }

    public void saveAutorisations(Autorisations autorisations) {
        autorisationsRepository.save(autorisations);
    }
}
