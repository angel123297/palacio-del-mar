package co.palaciodelmar.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountDTO {

    @NotBlank(message = "El nombre de la entidad bancaria es obligatorio")
    private String bankName;

    @NotBlank(message = "El tipo de cuenta es obligatorio")
    private String accountType;

    @Size(min = 4, max = 34, message = "El número de cuenta debe tener entre 4 y 34 caracteres")
    private String accountNumber;

    private String accountNumberMasked;

    @NotBlank(message = "El titular de la cuenta es obligatorio")
    private String holderName;

    private String documentId;
    private String documentType;
    private Instant updatedAt;
}
