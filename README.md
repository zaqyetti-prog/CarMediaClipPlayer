# Car Media Clip Player 🚗🎵 (Tema iOS 27 Glassmorphism)

Aplicativo Android desenvolvido especificamente para **Centrais Multimídia Automotivas** (compatível com **Android 7.0 Nougat até Android 8.1 Oreo e superiores**), focado em leitura de clipes de vídeo do Pen Drive USB com reprodução instantânea, memória de posição, menu com pastas gigantes e equalizador DSP completo com cortes de frequência.

---

## ⚡ Principais Recursos

1. **Auto-Play & Retomada Instantânea (Memória de Desligamento do Carro)**:
   - Ao abrir o app (ou quando a multimídia ligar após a partida do carro), o vídeo **já começa a rolar imediatamente** exatamente no segundo onde você parou.
   - Salva automaticamente o caminho do clipe, o nome e a posição em milissegundos a cada segundo e ao pausar/desligar.

2. **Menu com Pastas Gigantes (Touchscreen Automotivo)**:
   - Cards e botões extragrandes para facilitar o toque na multimídia enquanto você está no carro.
   - Varredura automática inteligente de todas as portas USB e pontos de montagem (`/storage`, `/mnt/usb_storage`, `/mnt/media_rw`, OTG).
   - Suporte a clipes `.mp4`, `.mkv`, `.avi`, `.mov`, `.webm`, `.ts`, `.flv`.
   - Miniaturas automáticas dos clipes em alta definição.

3. **Equalizador DSP Pro com Cortes de Frequência**:
   - **Bandas Gráficas de Frequência**: Ajuste fino com múltiplos controles deslizantes (ex: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz).
   - **Cortes de Frequência (Crossover HPF / LPF)**:
     - **Corte Sub-Grave (HPF)**: Corta frequências subgraves excessivas para evitar distorção e "sopros" nos alto-falantes de porta.
     - **Corte Agudos Extremos (LPF)**: Atenua frequências estridentes em tweeters automotivos para um som mais aveludado.
   - **Reforço de Graves (Bass Boost)**: Ganho dedicado para peso e pegada de graves.
   - **Loudness Gain Automotivo**: Ganho extra para clipes do YouTube ou shows com volume baixo.
   - **Presets Rápidos**: Flat, Grave Forte Automotivo, Voz/Médios, Rock Dinâmico.
   - Todas as configurações do equalizador são salvas na memória e mantidas mesmo após desligar a multimídia.

4. **Interface Futurista Estilo iOS 27 Glassmorphism**:
   - Design moderno com efeito vidro fosco (*frosted glass*), iluminação em azul neon / ciano e roxo dinâmico.
   - Controles limpos em tela cheia que desaparecem sozinhos para não atrapalhar o clipe.

5. **Compilação Automática no GitHub Actions**:
   - Arquivo `.github/workflows/build-apk.yml` pronto. Você envia o código para o GitHub e o GitHub gera o `.apk` pronto para instalar na sua central multimídia.

---

## 🚀 Como Criar o APK Grátis no GitHub (Passo a Passo)

Você não precisa ter o Android Studio instalado no seu computador. O próprio GitHub compilará o APK na nuvem:

### Passo 1: Criar um Repositório no GitHub
1. Acesse [github.com](https://github.com) e faça login na sua conta.
2. Clique no botão verde **"New"** (Novo Repositório).
3. Coloque o nome (exemplo: `CarMediaClipPlayer`).
4. Deixe como **Public** ou **Private** e clique em **Create repository**.

### Passo 2: Enviar os Arquivos para o GitHub
No seu computador, abra o terminal na pasta deste projeto:
```bash
git init
git add .
git commit -m "Initial commit - Car Clip Player iOS 27"
git branch -M main
git remote add origin https://github.com/SEU_USUARIO/SEU_REPOSITORIO.git
git push -u origin main
```

### Passo 3: Baixar o APK Compilado
1. No seu repositório no GitHub, clique na aba **"Actions"** no menu superior.
2. Você verá o fluxo **"Build Car Clip Player APK"** rodando automaticamente.
3. Aguarde cerca de 2 a 3 minutos até ficar verde com o ícone de concluído (✅).
4. Clique em cima da execução e vá até a seção **Artifacts** (Artefatos).
5. Clique em **`CarClipPlayer-APK`** para baixar o arquivo compactado contendo o APK:
   - `CarClipPlayer-Release-Android7+.apk` (ou `CarClipPlayer-Debug-Android7+.apk`).

---

## 📲 Como Instalar e Usar na Central Multimídia Android 8.1

1. Coloque o arquivo `.apk` no seu Pen Drive USB.
2. Conecte o Pen Drive na entrada USB da sua central multimídia.
3. Abra o aplicativo **"Gerenciador de Arquivos"** (File Manager) da central multimídia.
4. Clique no arquivo `.apk` e selecione **Instalar** (se pedir, autorize a instalação de fontes desconhecidas).
5. Abra o app **Car Clip Player**:
   - Autorize a permissão de acesso aos arquivos para ele poder ler o Pen Drive.
   - Se já tiver clipes no Pen Drive, o primeiro vídeo já começará a rolar na hora!
   - Clique em **"Pastas"** para ver suas pastas em tamanho grande.
   - Clique em **"Equalizador"** para ajustar o som ao gosto do sistema de áudio do seu carro.
   - Quando você desligar o carro e ligar novamente, abra o app e ele voltará tocando exatamente no ponto que parou!
