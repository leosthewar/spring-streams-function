# Alternativas a mostrar
## Mostrar Wrapper manual
## Mostrar Wrapper Automatico


## Pendientes principales
- En las ultimas pruebas, se observa que en los headers aparentemente se propaga el contexto de reactor, hacer pruebas de esto. No funciona siempre, no es fiable
- prueba usando  tuplas . e.g una tupla termina ok y otra con error


# Flujo General
- camino feliz
    - monitoring_event=> START (opcional)
        - la notificacion se genera wrapper(manual/automatico) (ChannelInterceptor/Kstream evaluar esta ), una vez se obtengan los headers
    - monitoring_event=> OK
      - Para este se tiene 2 alternativas
          - la notificacion se genera usando wrapper(manual/automatico) al finalizar la ejecucion del pipeline reactor
          - notificacion manual usando utilitario de la libreria ( esta es la opcion funcional mas viable)

- error handling
    - monitoring_event => KO
        - la notificacion se genera usando wrapper(manual/automatico) capturando el error usando operator onErrorContinue
    - dado que al implementar la libreria los pipelines reactivos(en los MS) van sin el onErrorContinue, se deberia tener un error handler global en la libreria, para evitar que los streams se desconecten al ocurrir errores
        - blindar ante errores no capturados en los pipelines reactivos
        - validar si es posible capturar el mensaje en estos casos para enviarla notificacion KO
        - este error handler global deberia capturar el error y enviar la notificacion KO
         

# Consideraciones
- se asume que todos los MS usan stream cloud streams & Functions
- lo mas conveniente es trabajar con headers, dado la complejidad y restricciones para trabajar con el contexto de reactor( ver si se puede dar la vuelta a reactor context)
- quitar los OnErrroContinue, para que estos sean gestionados por la libreria
- propagacion de headers( esto aplica si los el objetivo de la libreria es solo monitoreo, con una parte muy basica de funcional)
    - se debe asegurar en la medida de lo posible, que los headers no  se pierdan. para eso la libreria deberia crear utilitarios para construir los mensajes copiando los headers y los MS usar estos utilitarios 
    - de manera generial, deberia haber un MS consumer 0(inicial), donde se inyecten por primera vez los headers, de alli en adelante se deberian propagar
- crear utilitario dentro de libreria para enviar las notificaciones OK  de manera manual
- crear utilitario para mapear de Message<T> a Message<MonitorEvent> dependiendo del punto donde este
  - En este punto validar si con este componente la notificacion OK se portaria de manera automatica

# Pruebas en este proyecto
- Para probar flujo de error, enviar mensaje 'error' sin comillas en topic consume


# TO-DO
- Documentar un poco las diferencias entre el MonitoringProxy y el MonitoringWrapper

# Links
https://projectreactor.io/docs/core/release/reference/advanced-contextPropagation.html
https://www.baeldung.com/reactor-combine-streams
https://4comprehension.com/ultimate-guide-to-project-reactor-thread-locals-and-context-propagation/


https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream.html
https://javanexus.com/blog/mastering-error-handling-spring-integration
https://medium.com/@python-javascript-php-html-css/spring-integration-flows-with-dynamic-error-handling-controlling-error-channel-restrictions-858f9f35cbfc  
https://docs.spring.io/spring-cloud-stream/reference/kafka/kafka-binder/error-channels.html#:~:text=Starting%20with%20version%201.3%2C%20the%20binder%20unconditionally%20sends,this%20section%20on%20error%20handling%20for%20more%20information.


