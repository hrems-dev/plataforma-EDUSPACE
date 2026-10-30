-- Nombres y apellidos del apoderado por separado: el usuario de su cuenta se arma con su primer nombre y su primer apellido.
ALTER TABLE matriculas_publicas ADD COLUMN apoderado_nombres   VARCHAR(100);
ALTER TABLE matriculas_publicas ADD COLUMN apoderado_apellidos VARCHAR(150);
