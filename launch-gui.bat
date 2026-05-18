@echo off
"C:\Program Files\Git\bin\bash.exe" -l -c "cd \"/[path_to_howlman_repo]\" && mvn -P gui javafx:run; read -p 'Press enter to close...'"
