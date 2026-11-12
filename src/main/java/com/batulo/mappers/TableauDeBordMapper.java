package com.batulo.mappers;

import com.batulo.service.model.TableauDeBordModel;
import com.batulo.service.model.TableauDeBordResponse;
import org.springframework.stereotype.Component;

/** Mapper API : convertit uniquement TableauDeBordModel -> TableauDeBordResponse. */
@Component
public class TableauDeBordMapper {

    public TableauDeBordResponse toResponse(TableauDeBordModel model) {
        TableauDeBordModel.Abonnement a = model.abonnement();
        return new TableauDeBordResponse(
                a == null ? null : new TableauDeBordResponse.Abonnement(a.formule(), a.statut(), a.dateExpiration(),
                        a.joursRestants()),
                new TableauDeBordResponse.Gestionnaires(model.gestionnaires().actifs(),
                        model.gestionnaires().desactives()),
                new TableauDeBordResponse.Sondages(model.sondages().nonCommences(), model.sondages().enCours(),
                        model.sondages().termines()),
                model.nombreTotalReponses());
    }
}
