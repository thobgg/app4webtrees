# Comprobación de plausibilidad

**Herramientas › Comprobación de plausibilidad …** (Ctrl+Shift+P) o el icono **Comprobar** revisa todo el árbol, en la medida en que usted puede verlo, con 77 reglas. Los **errores** son contradicciones en los datos (defunción antes del nacimiento, antepasado de sí mismo); los **avisos** son cosas inusuales pero posibles (madre muy joven, padrino ya fallecido, posible duplicado, variante de lugar). Con api4webtrees 1.11 o posterior, las reglas de padrinos (024, 126) usan directamente los padrinos y testigos vinculados y buscan por nombre los de texto libre; con un módulo más antiguo leen la nota «Paten: …».

![](desktop-pruefung.jpg)


## Reglas y límites

Las reglas están agrupadas a la izquierda: **Cronología**, **Límites de edad**, **Estructura**, **Nombres**, **Fuentes**, **Lugares**, **Forma**. El grupo Forma comprueba la estructura de un archivo GEDCOM (enlaces, forma de las fechas, datos obligatorios); en wtWin solo se aplican las reglas visibles en el árbol. Para comprobar un archivo con todas las reglas, también GEDCOM 7: **Herramientas › Comprobar archivo GEDCOM …**. Un clic en una regla muestra solo sus hallazgos; **Todos los hallazgos** vuelve a mostrarlo todo.

- **Ajuste predefinido:** de estricto a tolerante; «Fuentes» activa además las reglas 420 y 421 (eventos y personas sin fuente). En cuanto se cambia algo, pasa a «Ajustes propios».
- Por regla: **Límite** (años o meses; se muestra el valor predeterminado), **Gravedad** (error o aviso), o desactivar la regla.
- **Estimar:** deducir el nacimiento y la defunción que faltan a partir del bautismo y el entierro, para que se apliquen las reglas de edad.
- **Todas las reglas y límites a los valores predeterminados** restablece; **Comprobar de nuevo** recalcula con los ajustes actuales.

## Trabajar con los hallazgos

Cada hallazgo indica la persona, la regla y los datos afectados.

- Un clic en el hallazgo abre a la persona; **Editar evento** cambia la fecha o el lugar directamente desde el hallazgo (con derechos de edición).
- **Marcar como comprobado:** los hallazgos que ha revisado y que se quedan como están (p. ej. un nacimiento tardío documentado) desaparecen de la lista. **Mostrar comprobados** los vuelve a mostrar, **Abrir de nuevo** quita la marca.
- La lista de marcas se guarda en este PC, por separado para cada servidor y árbol. **Exportar marcas …** e **Importar marcas …** la intercambian como archivo, p. ej. con otro investigador o para un segundo ordenador.

## Imprimir

**Imprimir** y **Guardar como PDF …** generan la lista actual de hallazgos con un resumen. La línea de arriba indica cuántas personas y familias se han comprobado y cuántos hallazgos son errores.
