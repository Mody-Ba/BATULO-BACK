package com.batulo.service;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.entity.Sondage;

/**
 * Copie profonde du contenu d'un sondage (sections > questions > options) dans un autre, pour la duplication et
 * les nouvelles versions. Ordres, configurations et valeurs des options sont conservés : les statistiques de deux
 * versions restent comparables.
 */
final class CopieSondage {

    private CopieSondage() {
    }

    static void copierContenu(Sondage source, Sondage cible) {
        for (Section sectionSource : source.getSections()) {
            Section section = new Section();
            section.setSondage(cible);
            section.setTitre(sectionSource.getTitre());
            section.setDescription(sectionSource.getDescription());
            section.setOrdre(sectionSource.getOrdre());
            for (Question questionSource : sectionSource.getQuestions()) {
                Question question = new Question();
                question.setSection(section);
                // Même clé : les statistiques d'une lignée (et la comparaison de copies) regroupent ces questions.
                question.setCle(questionSource.getCle());
                question.setLibelle(questionSource.getLibelle());
                question.setAide(questionSource.getAide());
                question.setType(questionSource.getType());
                question.setObligatoire(questionSource.isObligatoire());
                question.setOrdre(questionSource.getOrdre());
                question.setConfiguration(questionSource.getConfiguration().deepCopy());
                for (OptionQuestion optionSource : questionSource.getOptions()) {
                    OptionQuestion option = new OptionQuestion();
                    option.setQuestion(question);
                    option.setLibelle(optionSource.getLibelle());
                    option.setValeur(optionSource.getValeur());
                    option.setOrdre(optionSource.getOrdre());
                    question.getOptions().add(option);
                }
                section.getQuestions().add(question);
            }
            cible.getSections().add(section);
        }
    }
}
