# Libros

**Crear › Crear libro** convierte el árbol en un libro al estilo de los libros de familias impresos de una localidad: cada persona con eventos, fuentes, padrinos y notas, referencias a padres e hijos, retratos en el margen, índice general e índices.

## Tres libros

![](buch-vor-titel.jpg)
![](buch-vor-gen.jpg)
![](buch-nach-gen.jpg)
![](buch-fam.jpg)


- **Libro de antepasados:** todos los antepasados de la persona central por generación y número Sosa-Stradonitz, con fechas, bautismos, entierros, fuentes y notas. Las cuatro líneas de los abuelos, opcionalmente coloreadas en el margen.
- **Libro de descendientes:** todos los descendientes generación por generación, con cónyuges, hijos y referencias; números según Saragossa, d’Aboville, Henry o correlativos; colores de rama por cada hijo de la pareja de origen.
- **Libro de familias:** una entrada por familia, por orden alfabético o cronológico. Con la **estructura «por persona»** se convierte en el informe de personas: una entrada por persona con padres, matrimonios e hijos como referencias, índices sobre números de persona. También una entrada por familia, por orden alfabético o cronológico. Con un **filtro de lugar** se convierte en el libro de familias de una localidad. Necesita el árbol completo (api4webtrees 1.9 o posterior en el servidor). **Casas y granjas** (desde api4webtrees 1.15) añade una parte sobre los edificios: cada granja y casa del lugar del gestor de lugares (registros de lugar con un tipo bajo el lugar) con su historia y sus habitantes y propietarios en orden cronológico, cada uno con remisión a su familia; las familias remiten a su casa (H1, H2 …). Qué cuenta como casa lo decide el tipo del registro de lugar (casa, granja, molino, iglesia …; sin tipo, basta un número de casa en el nombre); barrios y pueblos se convierten en capítulos, y los lugares habitados que no son edificios van al anexo «Otros lugares». Si el registro de lugar lleva un número de tipo GOV (GEDCOM-L `2 _GOVTYPE`, api4webtrees 1.18.1 o posterior), ese número decide antes que el texto: granja, edificio, molino, finca, iglesia … son casas; pueblo, barrio, municipio … niveles superiores. Las opciones «Solo casas y granjas» e «Incluir lugares sin tipo» lo regulan. Los nombres de lugar pueden separarse con comas o puntos y comas.

## Ajustes

- **Datos:** generaciones (de 2 a 12), notas, fuentes, abreviar nombres de lugares, mostrar completos los antepasados repetidos (en lugar de «véase n.º»).
- **Personas vivas:** para publicar, las personas vivas aparecen completas, solo con su nombre o no aparecen; cuenta como viva quien lo es para webtrees o quien es más joven que el límite de edad (80 a 120 años). Solo libro de familias e informe de personas.
- **Prólogo desde archivo …** lee un archivo de texto en el prólogo; **Guardar/Cargar plantilla …** conserva el tipo de libro y todos los ajustes en un archivo `.wtbuch`, por ejemplo por lugar.
- **Aspecto:** imágenes, código de colores, prólogo (texto propio en la primera página), gráfico como página desplegable (A3, solo PDF).
- **Índices:** nombres, lugares, profesiones, fuentes, cada uno con los números de entrada.

## Guardar

![](buch-vor-reg.jpg)
![](buch-nach-titel.jpg)


**Guardar libro** pregunta el formato:

- **PDF** con marcadores y enlaces (un clic en «véase n.º» salta a la entrada).
- **DOCX** para seguir editándolo en Word o LibreOffice. Actualice allí una vez el índice general: haga clic en él y pulse F9 (LibreOffice: Herramientas › Actualizar › Índices y tablas).
- **Sitio web:** una carpeta para subir – página de inicio con prólogo y mosaicos, todas las entradas en una página con barra de letras, cada entrada también como página propia con navegación, casas, índices como páginas, hoja de estilo e imágenes como archivos. Poner antes las personas vivas en «solo nombre».
- **HTML** para un sitio web, **TeX** para maquetar con LaTeX, **Texto**.

En árboles grandes, cargar personas e imágenes lleva un momento; la ventana muestra el progreso.

## Árboles grandes con muchas imágenes

webtrees calcula cada miniatura en el servidor la primera vez que se pide; con miles de personas la primera pasada tarda. wtWin carga solo la imagen principal por persona, seis a la vez, muestra el progreso («Imágenes 2 340/8 900 – de este PC …, caché …, servidor …») y se puede detener con **Continuar sin las imágenes restantes**. Lo cargado una vez queda en la caché de este PC (`~/.cache/app4webtrees/medien/<servidor>/<árbol>/`, en Windows en la carpeta de datos de aplicación); la siguiente pasada y los cuadros después no necesitan nada del servidor. El diálogo del libro muestra bajo Presentación el tamaño de la caché y **Vaciar la caché de imágenes**.

Quien tenga una copia de la carpeta de medios de webtrees en este PC (por ejemplo porque sube por FTP) la indica como **Carpeta de medios en este PC**: las imágenes se leen entonces de ahí y se reducen localmente, sin servidor. Los demás dejan el campo vacío. webtrees sigue siendo siempre la fuente; la caché y la carpeta son solo copias.

**Visibilidad:** elija para quién es el libro: todo (vista del gestor), lo que pueden ver los miembros o lo que pueden ver los visitantes. El libro omite entonces lo que webtrees oculta a ese lector – eventos restringidos («Mostrar a miembros», «Mostrar a gestores»), datos sujetos a las reglas de privacidad del árbol, padrinos y familias que no puede ver, y los datos de personas vivas; su nombre solo se mantiene si el árbol muestra los nombres de vivos a ese lector, si no aparece «Privado». Los miembros ven por tanto más que los visitantes, pero nada de lo que solo se muestra a los gestores. Así se puede compartir un libro aunque lo cree un gestor. El sitio web siempre contiene solo lo que pueden ver los visitantes. Requiere api4webtrees 1.21 o posterior.
