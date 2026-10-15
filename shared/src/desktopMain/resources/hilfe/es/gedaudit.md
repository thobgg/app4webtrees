# gedaudit

**gedaudit** comprueba un archivo GEDCOM en busca de contradicciones y anomalías, con las mismas reglas que la comprobación de plausibilidad de wtWin, wtTux y wtMac. No necesita webtrees ni servidor: el archivo solo se lee, no se modifica nada y nada sale del equipo.

## Abrir un archivo

**Archivo › Abrir archivo GEDCOM …** (Ctrl+O) o el botón de la página de inicio. Los archivos abiertos recientemente aparecen en la página de inicio y en **Archivo › Abiertos recientemente**. Un archivo `.ged` también se abre con gedaudit con doble clic en el administrador de archivos.

Se leen GEDCOM 5.5, 5.5.1 y 7. El juego de caracteres se toma del archivo (UTF-8, UTF-16, ANSEL, ANSI); el reconocido se muestra en la línea sobre los hallazgos. Si el archivo se modificó en el otro programa, **Volver a cargar el archivo** (F5) recoge el nuevo estado.

## Comprobar

A la izquierda están las reglas por grupos, a la derecha los hallazgos. Cómo funcionan las reglas, los ajustes predefinidos, los límites y la marcación se explica en el capítulo [Comprobación de plausibilidad](hilfe:pruefung). La diferencia con wtWin: no hay hoja de persona ni edición, porque gedaudit no escribe en el archivo. Las correcciones se hacen en el programa de genealogía del que procede el archivo; después se exporta de nuevo y se vuelve a cargar en gedaudit.

## Forma del archivo

El grupo **Forma** solo existe para archivos: líneas que no son GEDCOM, enlaces a personas o familias que no existen, identificadores repetidos, fechas en una forma ilegible, personas sin nombre, más de un nacimiento o una defunción, enlaces familiares registrados solo por un lado, cabecera ausente o juego de caracteres de la cabecera distinto del archivo, y etiquetas fuera del estándar 5.5.1 o 7. Las dos últimas reglas están desactivadas por defecto, porque algunos programas las disparan mucho.

**Ver › Estructura del archivo …** (F6) muestra el árbol de etiquetas por tipo de registro con el número de apariciones. Las etiquetas del fabricante (con guion bajo) y las etiquetas fuera del estándar están resaltadas. Así se ve lo que un programa genera al exportar y lo que otro perderá al importar.

## Comparar dos archivos

**Archivo › Comparar con otro archivo …** (Ctrl+D) pone un segundo archivo frente al abierto, por ejemplo un estado anterior, la exportación de otro programa o el archivo de un colega. Las personas se emparejan por identificador y, si no, por nombre, año de nacimiento y sexo; las coincidencias cercanas aparecen como «probablemente la misma persona». A la izquierda los grupos: modificado, probablemente la misma, solo en uno u otro archivo, sin cambios. Un clic en una entrada muestra los campos modificados y ambas personas lado a lado. Los parientes se comparan por nombre, porque los identificadores pueden diferir entre archivos. Imprimir y PDF dan la lista de diferencias. No se fusiona ni se escribe nada.

## Ver una persona

Un clic en un hallazgo muestra abajo la **persona**: eventos con fecha, lugar y número de fuentes, los padres, los cónyuges con matrimonio y los hijos. Si el hallazgo afecta a dos personas, como un posible duplicado o madre e hijo, ambas aparecen lado a lado. Los identificadores (I123, F45) son los del archivo, así se encuentra la persona en el programa de origen. La línea divisoria sobre la tarjeta se puede arrastrar. **Ctrl+C** copia el texto de la persona elegida (nombre, identificador, eventos, padres, cónyuges, hijos) mientras ningún campo de entrada tenga el foco; un clic derecho en un hallazgo o en un evento ofrece **Copiar texto de la persona** y **Copiar fila**, y los valores de la tarjeta se pueden seleccionar con el ratón. El clic derecho ofrece también **Buscar en la metabúsqueda de CompGen …**: el apellido y, si se desea, un lugar se envían a meta.genealogy.net, y los resultados de GEDBAS, libros de familias locales en línea y otras bases de datos se abren en el navegador.

## Marcar como comprobado

Los hallazgos que ha revisado y que se quedan así se marcan; desaparecen de la lista y solo vuelven si algo cambia en ese punto. La lista de marcas se guarda en este PC, por archivo (según su ruta). **Guardar marcados …** y **Cargar marcados …** la intercambian como archivo, también con wtWin.

## Imprimir

**Archivo › Imprimir** (Ctrl+P) y **Guardar como PDF …** generan la lista actual de hallazgos con un resumen.

## Idioma y apariencia

**Ver › Idioma** cambia la interfaz (alemán, inglés, francés, neerlandés, español), **Ver › Apariencia** alterna entre claro y oscuro.

## GEDCOM-L

gedaudit conoce el addendum GEDCOM-L de GEDCOM 5.5.1. En la ventana **Estructura del archivo** sus etiquetas (_LOC, _GODP, _UID …) aparecen como «GEDCOM-L» y no como etiquetas de fabricante; la línea de cabecera indica la versión del addendum si el archivo la declara. La ficha de la persona muestra padrinos y testigos sin registro propio (_GODP, _WITN), la dirección y el identificador GOV, el código postal y el localizador Maidenhead del lugar. Las referencias a registros de lugar (`3 _LOC` en un lugar, `1 _LOC` en un registro de lugar) cuyo destino falta se señalan como cualquier otra referencia rota.
