// Los ids de Mongo son 24 caracteres hexadecimales. Los datos de respaldo del
// frontend (ids como "fx-3") no existen en la base de datos y no se pueden reservar.
export const isObjectId = (value) => /^[a-f\d]{24}$/i.test(String(value ?? ''));
