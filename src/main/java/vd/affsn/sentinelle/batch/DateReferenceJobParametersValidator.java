package vd.affsn.sentinelle.batch;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;

/**
 * Valide les paramètres du job {@code calculerTensionNocturne}.
 *
 * <p>Seuls {@value #DATE_REFERENCE} (obligatoire, texte {@code AAAA-MM-JJ} représentant une date
 * calendaire réelle) et {@value #RUN_ID} (posé par l'incrémenteur) sont acceptés ; tout autre
 * paramètre est refusé, ce qui évite qu'une entrée inattendue modifie l'identité de l'instance.
 *
 * <p>{@value #RUN_ID}, s'il est présent, doit être de type {@code Long} et STRICTEMENT positif :
 * l'incrémenteur ({@code RunIdIncrementer}) ne sait relire qu'un {@code Long}, et une valeur d'un
 * autre type acceptée ici ferait échouer tous les lancements suivants (lanceur et planification)
 * jusqu'à intervention manuelle. Un lancement refusé ne crée aucune instance de job. Le message
 * d'erreur ne recopie jamais la valeur reçue.
 */
public final class DateReferenceJobParametersValidator implements JobParametersValidator {

    public static final String DATE_REFERENCE = "dateReference";
    public static final String RUN_ID = "run.id";

    private static final Set<String> AUTORISES = Set.of(DATE_REFERENCE, RUN_ID);
    /** Exactement quatre chiffres d'année : refuse les années signées ou à plus de 4 chiffres. */
    private static final Pattern FORMAT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Override
    public void validate(JobParameters parameters) throws JobParametersInvalidException {
        if (parameters == null) {
            throw new JobParametersInvalidException("Paramètres de job absents");
        }
        for (String cle : parameters.getParameters().keySet()) {
            if (!AUTORISES.contains(cle)) {
                throw new JobParametersInvalidException("Paramètre de job non autorisé");
            }
        }
        if (parameters.getParameters().containsKey(RUN_ID)) {
            Object runId = parameters.getParameters().get(RUN_ID).getValue();
            if (!(runId instanceof Long numero) || numero <= 0) {
                throw new JobParametersInvalidException("Paramètre run.id : entier long strictement positif attendu");
            }
        }
        Object brut = parameters.getParameters().containsKey(DATE_REFERENCE)
                ? parameters.getParameters().get(DATE_REFERENCE).getValue()
                : null;
        if (!(brut instanceof String texte)) {
            throw new JobParametersInvalidException("Paramètre dateReference obligatoire (texte AAAA-MM-JJ)");
        }
        analyser(texte);
    }

    /** Analyse stricte ; le message d'erreur ne reprend pas la valeur reçue. */
    static LocalDate analyser(String texte) throws JobParametersInvalidException {
        if (texte == null || !FORMAT.matcher(texte).matches()) {
            throw new JobParametersInvalidException("dateReference : format AAAA-MM-JJ attendu");
        }
        try {
            // ISO_LOCAL_DATE utilise ResolverStyle.STRICT : 2026-02-30 est refusé.
            return LocalDate.parse(texte, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new JobParametersInvalidException("dateReference : date calendaire invalide");
        }
    }
}
