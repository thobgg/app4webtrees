# Listas

La ventana **Crear lista** se abre con **Crear › Lista de antepasados**, **Lista de descendientes**, **Lista de eventos** o con el icono **Lista**. A la izquierda el tipo de lista con sus ajustes, a la derecha la vista previa de las páginas terminadas; abajo **Imprimir** y **Guardar como PDF …**.

## A partir de la persona central

![](liste-ahnen.jpg)
![](liste-stamm.jpg)
![](liste-taufpaten.jpg)
![](liste-ahnenwertung.jpg)


- **Lista de antepasados:** todos los antepasados por generación con números Sosa-Stradonitz, hasta 12 generaciones; quien aparece más de una vez remite a su primer número (implexo).
- **Antepasados más remotos:** los antepasados en los que termina la investigación, según las cuatro líneas de los abuelos.
- **Línea paterna** y **Línea materna:** la línea del padre o de la madre generación por generación, con los cónyuges.
- **Completitud de la ascendencia:** lo completa que está investigada cada generación, en forma de tabla con implexo.
- **Lista de descendientes:** todos los descendientes con números, cónyuges y matrimonios, hasta 10 generaciones. Numeración según Saragossa, d’Aboville, Henry o correlativa.
- **Descendientes por generación:** cuántos descendientes hay en cada generación: hombres, mujeres, vivos, portadores del apellido.
- **Ficha personal:** todos los datos de la persona central en una página.

## Sobre todo el árbol

![](liste-namen.jpg)
![](liste-orte.jpg)
![](liste-ereignisse.jpg)
![](liste-spitzenahnen.jpg)


- **Lista de eventos:** nacimientos y defunciones de todas las personas visibles en orden cronológico; opcionalmente como **calendario de aniversarios** por mes y día, con filtro de lugar, p. ej. para la circular familiar.
- **Apellidos:** todos los apellidos con su número, período y los lugares más frecuentes.
- **Lugares:** todos los lugares tal como figuran en los eventos: **solo lugares** (eventos, personas, período), **con apellidos** o **con personas** y sus eventos allí. **Detalles de lugares** añade el identificador GOV y las coordenadas del gestor de lugares; **Notas** y **Fuentes**, las del registro de lugar.
- **Familias:** todos los matrimonios con la boda y el número de hijos, por orden alfabético o cronológico.
- **Documentación con fuentes:** lo bien documentado que está el árbol: personas y eventos con fuente en porcentaje, fotos, por tipo de evento, y quién no tiene todavía ninguna fuente.
- **Profesiones y religiones:** quién tuvo qué profesión o perteneció a qué religión.
- **Padrinos:** todos los bautismos con sus padrinos en orden cronológico; con api4webtrees 1.11 o posterior a partir de los padrinos vinculados y de los de texto libre («vinculados: 2 de 3» indica cuántos tienen registro propio); si no, a partir de la nota «Paten: …».

Las listas sobre todo el árbol necesitan el árbol completo; el servidor debe tener api4webtrees 1.9 o posterior. Con el árbol genealógico en este PC siempre es así.

La vista previa muestra las primeras páginas; si hay más, debajo aparece «… y n páginas más en el PDF». Cada página lleva abajo «Creado con wtWin el …».

## ADN

**Crear › ADN …** abre la vista de ADN de la persona de partida, con cuatro apartados. **Líneas** muestra de qué antepasados puede tener el ADN-Y, el ADN mitocondrial (ADNmt) o el ADN-X, y qué descendientes pueden tenerlo de ella. Arriba se eligen la línea, la dirección y el número de generaciones.

- **ADN-Y:** del padre al hijo varón. En una mujer se muestra la línea de su padre, con la indicación de que ella misma no tiene cromosoma Y.
- **ADNmt:** de la madre a todos los hijos; solo las hijas lo transmiten.
- **ADN-X:** un hombre tiene su X de la madre y lo transmite a sus hijas, una mujer tiene uno de cada progenitor y transmite un X a todos sus hijos. En cada generación se indica cuántos de los posibles antepasados X figuran en el árbol.

Los antepasados aparecen por generación con su número de ascendencia, los descendientes como lista sangrada: son las personas que podrían hacerse una prueba para la línea. Un clic convierte a la persona en persona de partida en la ventana principal; **Partir de …** vuelve a calcular las líneas para ella.

Las líneas se deducen de los parentescos registrados y no son el resultado de una prueba; en el ADN-X, «posible» no significa que realmente se comparta ADN-X. Solo cuentan los padres biológicos: en un hijo adoptivo o de acogida y en una persona sin sexo registrado, la línea termina con una indicación. La entrada de menú solo aparece si en el servidor está instalado el módulo de webtrees dna4webtrees; él calcula las líneas.

**Pruebas** muestra las pruebas de ADN y los haplogrupos registrados de la persona y de las demás personas de la misma línea, con una indicación si los haplogrupos de una línea se contradicen. **Coincidencias** muestra las coincidencias de ADN registradas: el ADN compartido en centimorgans, el parentesco en el árbol con los antepasados comunes, la cantidad habitual para ese parentesco e indicaciones, por ejemplo si el valor está fuera del rango habitual. **¿Quién podría hacerse la prueba?** indica las personas vivas de la línea Y y de la línea materna. Las pruebas y las coincidencias se introducen en el navegador, en la pestaña **ADN** de la persona.
