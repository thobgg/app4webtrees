# Libros

**Crear › Crear libro** convierte el árbol en un libro al estilo de los libros de familias impresos de una localidad: cada persona con eventos, fuentes, padrinos y notas, referencias a padres e hijos, retratos en el margen, índice general e índices.

## Tres libros

- **Libro de antepasados:** todos los antepasados de la persona central por generación y número Sosa-Stradonitz, con fechas, bautismos, entierros, fuentes y notas. Las cuatro líneas de los abuelos, opcionalmente coloreadas en el margen.
- **Libro de descendientes:** todos los descendientes generación por generación, con cónyuges, hijos y referencias; números según Saragossa, d’Aboville, Henry o correlativos; colores de rama por cada hijo de la pareja de origen.
- **Libro de familias:** una entrada por familia, por orden alfabético o cronológico. Con un **filtro de lugar** se convierte en el libro de familias de una localidad. Necesita el árbol completo (api4webtrees 1.9 o posterior en el servidor). **Casas y granjas** (desde api4webtrees 1.15) añade una parte sobre los edificios: cada granja y casa del lugar del gestor de lugares (registros de lugar con un tipo bajo el lugar) con su historia y sus habitantes y propietarios en orden cronológico, cada uno con remisión a su familia; las familias remiten a su casa (H1, H2 …). Qué cuenta como casa lo decide el tipo del registro de lugar (casa, granja, molino, iglesia …; sin tipo, basta un número de casa en el nombre); barrios y pueblos se convierten en capítulos, y los lugares habitados que no son edificios van al anexo «Otros lugares». Las opciones «Solo casas y granjas» e «Incluir lugares sin tipo» lo regulan. Los nombres de lugar pueden separarse con comas o puntos y comas.

## Ajustes

- **Datos:** generaciones (de 2 a 12), notas, fuentes, abreviar nombres de lugares, mostrar completos los antepasados repetidos (en lugar de «véase n.º»).
- **Aspecto:** imágenes, código de colores, prólogo (texto propio en la primera página), gráfico como página desplegable (A3, solo PDF).
- **Índices:** nombres, lugares, profesiones, fuentes, cada uno con los números de entrada.

## Guardar

**Guardar libro** pregunta el formato:

- **PDF** con marcadores y enlaces (un clic en «véase n.º» salta a la entrada).
- **DOCX** para seguir editándolo en Word o LibreOffice. Actualice allí una vez el índice general: haga clic en él y pulse F9 (LibreOffice: Herramientas › Actualizar › Índices y tablas).
- **HTML** para un sitio web, **TeX** para maquetar con LaTeX, **Texto**.

En árboles grandes, cargar personas e imágenes lleva un momento; la ventana muestra el progreso.
