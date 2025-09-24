# Alternativas a mostrar
## Mostrar Wrapper manual
## Mostrar Wrapper Automatico


## Pendientes principales
- probar funcion tipo consumer - ok 
- probar error - ok 
- crear un diagrama sencillo para explicar
- probar proyecto en la VDI
- usar tuplas para flujo error y ok( optional) 
- En las ultimas pruebas, se observa que en los headers aparentemente se propaga el contexto de reactor, hacer pruebas de esto

# Flujo General
- camino feliz
    - monitoring_event=> START
        - la notificacion se genera usando ChannelInterceptor/Kstream, una vez se obtengan los headers
    - monitoring_event=> OK
        -  la notificacion se genera usando wrapper(manual/automatico) al finalizar la ejecucion del pipeline reactor

- error handling
    - monitoring_event => KO
        - la notificacion se genera usando wrapper(manual/automatico) capturando el error usando operator onErrorContinue

# Consideraciones
- se asume que todos los MS usan stream cloud streams & Funtions
- lo mas conveniente es trabajar con headers, dado la complejidad y restricciones para trabajar con el contexto de reactor( ver si se puede dar la vuelta a reactor context)
- quitar los OnErrroContinue, para que estos sean gestionados por la libreria
- propagacion de headers( esto aplica si los el objetivo de la libreria es solo monitoreo, con una parte muy basica de funcional)
    - se debe asegurar en la medida de lo posible, que los headers no  se pierdan. para eso la libreria deberia crear utilitarios para construir los mensajes copiando los headers y los MS usar estos utilitarios 
    - de manera generial, deberia haber un MS consumer 0(inicial), donde se inyecten por primera vez los headers, de alli en adelante se deberian propagar
- Validar / proponer alcance de la libreria
    - si hay dependencia o no con temas funcionales
    - si la libreria deberia ser un 
