"""Sprachpakete (Teilübersetzungen: gemeinsame Texte und Dateibaum). Fehlende Schlüssel fallen auf die App-Sprache zurück.
Reihenfolge der Werte = Schlüsselreihenfolge in KEYS (86 Einträge, Quelle: values/strings.xml, Präfixe common_ und filetree_)."""
import json
from pathlib import Path

# (id, locale, Anzeigename, Werte)
PACKS = [
    ("fr", "fr", "Français", [
        "Aa", "Annuler", "Actualiser", "Actuel", "Nom d’utilisateur manquant", "Fichier", "Échec de la désinstallation.", "Éditeur", "Supprimer", "Détecté : %1$s",
        "Réessayer", "Remplacer par", "Git", "En attente d’une autre installation …", "Échec de l’installation.", "%1$s installé.", "Aucun résultat", "Échec du clonage.", "Supprimer", "Nom", "Aucun commit à modifier pour l’instant.",
        "Dossier", "Aperçu", "Projet", "Fermer", "Enregistrer", "L’environnement Termux n’est pas encore configuré (terminez l’introduction).", "Jeton manquant", "Passer", "Renommer", "Mettre à jour",
        "Aperçu", "Mot", "Retour", "Erreur", "Échec de l’action.", "Échec de la suppression.", "Options de l’arborescence", "Mode de tri", "Groupé (dossiers cachés, dossiers, fichiers cachés, fichiers)", "Mélangé (une seule liste)",
        "Trier par", "Nom", "Type", "Taille", "Modifié", "Décroissant", "Affichage", "Fichiers", "Projet", "Module (Android)",
        "Afficher les fichiers cachés", "manifestes", "java / kotlin", "res", "assets", "autres fichiers", "Scripts Gradle", "Fichiers du projet", "Outils de plugin (PT)", "Aucun plugin d’outils installé",
        "Les outils ne sont pas disponibles", "Démarrer", "Installer les prérequis", "« %1$s » nécessite des programmes qui ne sont pas encore installés. Les installer maintenant ?", "Installer", "Installation des prérequis…", "En cours…", "Arrêté : délai dépassé", "Terminé (code de sortie %1$d)", "Nouveau fichier",
        "Nouveau dossier", "Propriétés", "Confirmer la suppression", "Voulez-vous vraiment supprimer « %1$s » ? Cette action est irréversible.", "Nom", "Chemin", "Type", "Taille", "Modifié", "Autorisations",
        "%1$d éléments", "1 élément", "Aucun élément", "Ouvrir dans l’éditeur", "Chargement…", "Actions du plugin"]),
    ("es", "es", "Español", [
        "Aa", "Cancelar", "Actualizar", "Actual", "Falta el nombre de usuario", "Archivo", "Error al desinstalar.", "Editor", "Quitar", "Detectado: %1$s",
        "Reintentar", "Reemplazar por", "Git", "Esperando otra instalación …", "Error en la instalación.", "%1$s instalado.", "Sin coincidencias", "Error al clonar.", "Eliminar", "Nombre", "Todavía no hay ningún commit que modificar.",
        "Carpeta", "Vista previa", "Proyecto", "Cerrar", "Guardar", "El entorno de Termux aún no está configurado (completa la introducción).", "Falta el token", "Omitir", "Renombrar", "Actualizar",
        "Vista previa", "Palabra", "Atrás", "Error", "La acción ha fallado.", "Error al eliminar.", "Opciones del árbol de archivos", "Modo de orden", "Agrupado (carpetas ocultas, carpetas, archivos ocultos, archivos)", "Mezclado (una sola lista)",
        "Ordenar por", "Nombre", "Tipo", "Tamaño", "Modificado", "Descendente", "Vista", "Archivos", "Proyecto", "Módulo (Android)",
        "Mostrar archivos ocultos", "manifiestos", "java / kotlin", "res", "assets", "otros archivos", "Scripts de Gradle", "Archivos del proyecto", "Herramientas de plugins (PT)", "No hay plugins de herramientas instalados",
        "Las herramientas no están disponibles", "Iniciar", "Instalar requisitos", "«%1$s» necesita programas que aún no están instalados. ¿Instalarlos ahora?", "Instalar", "Instalando requisitos…", "En ejecución…", "Detenido: tiempo límite superado", "Finalizado (código de salida %1$d)", "Archivo nuevo",
        "Carpeta nueva", "Propiedades", "Confirmar eliminación", "¿Seguro que quieres eliminar «%1$s»? Esta acción no se puede deshacer.", "Nombre", "Ruta", "Tipo", "Tamaño", "Modificado", "Permisos",
        "%1$d elementos", "1 elemento", "Sin elementos", "Abrir en el editor", "Cargando…", "Acciones del plugin"]),
    ("it", "it", "Italiano", [
        "Aa", "Annulla", "Aggiorna", "Attuale", "Nome utente mancante", "File", "Disinstallazione non riuscita.", "Editor", "Rimuovi", "Rilevato: %1$s",
        "Riprova", "Sostituisci con", "Git", "In attesa di un’altra installazione …", "Installazione non riuscita.", "%1$s installato.", "Nessuna corrispondenza", "Clonazione non riuscita.", "Elimina", "Nome", "Nessun commit da modificare.",
        "Cartella", "Anteprima", "Progetto", "Chiudi", "Salva", "L’ambiente Termux non è ancora configurato (completa l’introduzione).", "Token mancante", "Salta", "Rinomina", "Aggiorna",
        "Anteprima", "Parola", "Indietro", "Errore", "Azione non riuscita.", "Eliminazione non riuscita.", "Opzioni dell’albero dei file", "Modalità di ordinamento", "Raggruppato (cartelle nascoste, cartelle, file nascosti, file)", "Misto (un unico elenco)",
        "Ordina per", "Nome", "Tipo", "Dimensione", "Modificato", "Decrescente", "Vista", "File", "Progetto", "Modulo (Android)",
        "Mostra file nascosti", "manifest", "java / kotlin", "res", "assets", "altri file", "Script Gradle", "File del progetto", "Strumenti plugin (PT)", "Nessun plugin di strumenti installato",
        "Gli strumenti non sono disponibili", "Avvia", "Installa i requisiti", "“%1$s” richiede programmi non ancora installati. Installarli ora?", "Installa", "Installazione dei requisiti…", "In esecuzione…", "Interrotto: tempo limite superato", "Terminato (codice di uscita %1$d)", "Nuovo file",
        "Nuova cartella", "Proprietà", "Conferma eliminazione", "Eliminare davvero “%1$s”? L’operazione non può essere annullata.", "Nome", "Percorso", "Tipo", "Dimensione", "Modificato", "Permessi",
        "%1$d elementi", "1 elemento", "Nessun elemento", "Apri nell’editor", "Caricamento…", "Azioni del plugin"]),
    ("pt-BR", "pt-BR", "Português (Brasil)", [
        "Aa", "Cancelar", "Atualizar", "Atual", "Nome de usuário ausente", "Arquivo", "Falha ao desinstalar.", "Editor", "Remover", "Detectado: %1$s",
        "Tentar novamente", "Substituir por", "Git", "Aguardando outra instalação …", "Falha na instalação.", "%1$s instalado.", "Nenhuma correspondência", "Falha ao clonar.", "Excluir", "Nome", "Ainda não há commit para alterar.",
        "Pasta", "Pré-visualização", "Projeto", "Fechar", "Salvar", "O ambiente Termux ainda não foi configurado (conclua a introdução).", "Token ausente", "Pular", "Renomear", "Atualizar",
        "Pré-visualização", "Palavra", "Voltar", "Erro", "A ação falhou.", "Falha ao excluir.", "Opções da árvore de arquivos", "Modo de ordenação", "Agrupado (pastas ocultas, pastas, arquivos ocultos, arquivos)", "Misturado (uma única lista)",
        "Ordenar por", "Nome", "Tipo", "Tamanho", "Modificado", "Decrescente", "Visualização", "Arquivos", "Projeto", "Módulo (Android)",
        "Mostrar arquivos ocultos", "manifestos", "java / kotlin", "res", "assets", "outros arquivos", "Scripts do Gradle", "Arquivos do projeto", "Ferramentas de plugin (PT)", "Nenhum plugin de ferramentas instalado",
        "As ferramentas não estão disponíveis", "Iniciar", "Instalar requisitos", "“%1$s” precisa de programas que ainda não estão instalados. Instalar agora?", "Instalar", "Instalando requisitos…", "Em execução…", "Interrompido: tempo limite excedido", "Concluído (código de saída %1$d)", "Novo arquivo",
        "Nova pasta", "Propriedades", "Confirmar exclusão", "Tem certeza de que deseja excluir “%1$s”? Esta ação não pode ser desfeita.", "Nome", "Caminho", "Tipo", "Tamanho", "Modificado", "Permissões",
        "%1$d itens", "1 item", "Nenhum item", "Abrir no editor", "Carregando…", "Ações do plugin"]),
    ("nl", "nl", "Nederlands", [
        "Aa", "Annuleren", "Vernieuwen", "Huidig", "Gebruikersnaam ontbreekt", "Bestand", "Verwijderen mislukt.", "Editor", "Verwijderen", "Gedetecteerd: %1$s",
        "Opnieuw proberen", "Vervangen door", "Git", "Wachten op een andere installatie …", "Installatie mislukt.", "%1$s geïnstalleerd.", "Geen resultaten", "Klonen mislukt.", "Verwijderen", "Naam", "Nog geen commit om te wijzigen.",
        "Map", "Voorbeeld", "Project", "Sluiten", "Opslaan", "De Termux-omgeving is nog niet ingesteld (rond de introductie af).", "Token ontbreekt", "Overslaan", "Hernoemen", "Bijwerken",
        "Voorbeeld", "Woord", "Terug", "Fout", "Actie mislukt.", "Verwijderen mislukt.", "Opties voor bestandsboom", "Sorteermodus", "Gegroepeerd (verborgen mappen, mappen, verborgen bestanden, bestanden)", "Gemengd (één lijst)",
        "Sorteren op", "Naam", "Type", "Grootte", "Gewijzigd", "Aflopend", "Weergave", "Bestanden", "Project", "Module (Android)",
        "Verborgen bestanden tonen", "manifesten", "java / kotlin", "res", "assets", "overige bestanden", "Gradle-scripts", "Projectbestanden", "Plugin-tools (PT)", "Geen tool-plugins geïnstalleerd",
        "Tools zijn niet beschikbaar", "Starten", "Vereisten installeren", "“%1$s” heeft programma’s nodig die nog niet geïnstalleerd zijn. Nu installeren?", "Installeren", "Vereisten worden geïnstalleerd…", "Wordt uitgevoerd…", "Gestopt: tijdslimiet overschreden", "Voltooid (afsluitcode %1$d)", "Nieuw bestand",
        "Nieuwe map", "Eigenschappen", "Verwijderen bevestigen", "Weet je zeker dat je “%1$s” wilt verwijderen? Dit kan niet ongedaan worden gemaakt.", "Naam", "Pad", "Type", "Grootte", "Gewijzigd", "Rechten",
        "%1$d items", "1 item", "Geen items", "Openen in editor", "Laden…", "Plugin-acties"]),
    ("pl", "pl", "Polski", [
        "Aa", "Anuluj", "Odśwież", "Bieżący", "Brak nazwy użytkownika", "Plik", "Odinstalowanie nie powiodło się.", "Edytor", "Usuń", "Wykryto: %1$s",
        "Spróbuj ponownie", "Zamień na", "Git", "Oczekiwanie na inną instalację …", "Instalacja nie powiodła się.", "Zainstalowano: %1$s.", "Brak dopasowań", "Klonowanie nie powiodło się.", "Usuń", "Nazwa", "Nie ma jeszcze commitu do poprawienia.",
        "Folder", "Podgląd", "Projekt", "Zamknij", "Zapisz", "Środowisko Termux nie jest jeszcze skonfigurowane (ukończ wprowadzenie).", "Brak tokenu", "Pomiń", "Zmień nazwę", "Aktualizuj",
        "Podgląd", "Słowo", "Wstecz", "Błąd", "Akcja nie powiodła się.", "Usuwanie nie powiodło się.", "Opcje drzewa plików", "Tryb sortowania", "Grupowane (ukryte foldery, foldery, ukryte pliki, pliki)", "Mieszane (jedna lista)",
        "Sortuj według", "Nazwa", "Typ", "Rozmiar", "Zmodyfikowano", "Malejąco", "Widok", "Pliki", "Projekt", "Moduł (Android)",
        "Pokaż ukryte pliki", "manifesty", "java / kotlin", "res", "assets", "inne pliki", "Skrypty Gradle", "Pliki projektu", "Narzędzia wtyczek (PT)", "Brak zainstalowanych wtyczek narzędzi",
        "Narzędzia są niedostępne", "Uruchom", "Zainstaluj wymagania", "„%1$s” wymaga programów, które nie są jeszcze zainstalowane. Zainstalować je teraz?", "Zainstaluj", "Instalowanie wymagań…", "Uruchomione…", "Zatrzymano: przekroczono limit czasu", "Zakończono (kod wyjścia %1$d)", "Nowy plik",
        "Nowy folder", "Właściwości", "Potwierdź usunięcie", "Czy na pewno usunąć „%1$s”? Tej operacji nie można cofnąć.", "Nazwa", "Ścieżka", "Typ", "Rozmiar", "Zmodyfikowano", "Uprawnienia",
        "Elementów: %1$d", "1 element", "Brak elementów", "Otwórz w edytorze", "Ładowanie…", "Akcje wtyczki"]),
    ("tr", "tr", "Türkçe", [
        "Aa", "İptal", "Yenile", "Geçerli", "Kullanıcı adı eksik", "Dosya", "Kaldırma başarısız.", "Düzenleyici", "Kaldır", "Algılandı: %1$s",
        "Yeniden dene", "Şununla değiştir", "Git", "Başka bir kurulum bekleniyor …", "Kurulum başarısız.", "%1$s kuruldu.", "Eşleşme yok", "Klonlama başarısız.", "Sil", "Ad", "Henüz değiştirilecek bir commit yok.",
        "Klasör", "Önizleme", "Proje", "Kapat", "Kaydet", "Termux ortamı henüz kurulmadı (tanıtımı tamamlayın).", "Belirteç eksik", "Atla", "Yeniden adlandır", "Güncelle",
        "Önizleme", "Sözcük", "Geri", "Hata", "İşlem başarısız.", "Silme başarısız.", "Dosya ağacı seçenekleri", "Sıralama modu", "Gruplu (gizli klasörler, klasörler, gizli dosyalar, dosyalar)", "Karışık (tek liste)",
        "Sıralama ölçütü", "Ad", "Tür", "Boyut", "Değiştirilme", "Azalan", "Görünüm", "Dosyalar", "Proje", "Modül (Android)",
        "Gizli dosyaları göster", "manifestolar", "java / kotlin", "res", "assets", "diğer dosyalar", "Gradle betikleri", "Proje dosyaları", "Eklenti araçları (PT)", "Araç eklentisi kurulu değil",
        "Araçlar kullanılamıyor", "Başlat", "Gereksinimleri kur", "“%1$s” henüz kurulu olmayan programlara ihtiyaç duyuyor. Şimdi kurulsun mu?", "Kur", "Gereksinimler kuruluyor…", "Çalışıyor…", "Durduruldu: zaman sınırı aşıldı", "Tamamlandı (çıkış kodu %1$d)", "Yeni dosya",
        "Yeni klasör", "Özellikler", "Silmeyi onayla", "“%1$s” öğesini silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.", "Ad", "Yol", "Tür", "Boyut", "Değiştirilme", "İzinler",
        "%1$d öğe", "1 öğe", "Öğe yok", "Düzenleyicide aç", "Yükleniyor…", "Eklenti eylemleri"]),
    ("ru", "ru", "Русский", [
        "Aa", "Отмена", "Обновить", "Текущий", "Не указано имя пользователя", "Файл", "Не удалось удалить.", "Редактор", "Удалить", "Обнаружено: %1$s",
        "Повторить", "Заменить на", "Git", "Ожидание другой установки …", "Не удалось установить.", "%1$s установлено.", "Совпадений нет", "Не удалось клонировать.", "Удалить", "Имя", "Пока нет коммита для изменения.",
        "Папка", "Предпросмотр", "Проект", "Закрыть", "Сохранить", "Среда Termux ещё не настроена (завершите вводный экран).", "Не указан токен", "Пропустить", "Переименовать", "Обновить",
        "Предпросмотр", "Слово", "Назад", "Ошибка", "Не удалось выполнить действие.", "Не удалось удалить.", "Параметры дерева файлов", "Режим сортировки", "По группам (скрытые папки, папки, скрытые файлы, файлы)", "Смешанный (один список)",
        "Сортировать по", "Имя", "Тип", "Размер", "Изменён", "По убыванию", "Вид", "Файлы", "Проект", "Модуль (Android)",
        "Показывать скрытые файлы", "манифесты", "java / kotlin", "res", "assets", "прочие файлы", "Скрипты Gradle", "Файлы проекта", "Инструменты плагинов (PT)", "Плагины инструментов не установлены",
        "Инструменты недоступны", "Запустить", "Установить требования", "Для «%1$s» нужны программы, которые ещё не установлены. Установить их сейчас?", "Установить", "Установка требований…", "Выполняется…", "Остановлено: превышено время ожидания", "Завершено (код выхода %1$d)", "Новый файл",
        "Новая папка", "Свойства", "Подтвердите удаление", "Вы уверены, что хотите удалить «%1$s»? Это действие нельзя отменить.", "Имя", "Путь", "Тип", "Размер", "Изменён", "Права доступа",
        "Элементов: %1$d", "1 элемент", "Нет элементов", "Открыть в редакторе", "Загрузка…", "Действия плагина"]),
    ("ja", "ja", "日本語", [
        "Aa", "キャンセル", "更新", "現在", "ユーザー名がありません", "ファイル", "アンインストールに失敗しました。", "エディター", "削除", "検出: %1$s",
        "再試行", "置換後", "Git", "別のインストールを待機中…", "インストールに失敗しました。", "%1$s をインストールしました。", "一致する項目がありません", "クローンに失敗しました。", "削除", "名前", "修正できるコミットがまだありません。",
        "フォルダー", "プレビュー", "プロジェクト", "閉じる", "保存", "Termux 環境がまだ設定されていません（オンボーディングを完了してください）。", "トークンがありません", "スキップ", "名前を変更", "アップデート",
        "プレビュー", "単語", "戻る", "エラー", "操作に失敗しました。", "削除に失敗しました。", "ファイルツリーのオプション", "並べ替えモード", "グループ化（隠しフォルダー、フォルダー、隠しファイル、ファイル）", "混合（1つのリスト）",
        "並べ替え基準", "名前", "種類", "サイズ", "更新日時", "降順", "表示", "ファイル", "プロジェクト", "モジュール（Android）",
        "隠しファイルを表示", "マニフェスト", "java / kotlin", "res", "assets", "その他のファイル", "Gradle スクリプト", "プロジェクトファイル", "プラグインツール（PT）", "ツールプラグインがインストールされていません",
        "ツールは利用できません", "開始", "必要条件をインストール", "「%1$s」には、まだインストールされていないプログラムが必要です。今すぐインストールしますか？", "インストール", "必要条件をインストール中…", "実行中…", "停止しました：制限時間を超えました", "完了（終了コード %1$d）", "新規ファイル",
        "新規フォルダー", "プロパティ", "削除の確認", "「%1$s」を削除してもよろしいですか？この操作は元に戻せません。", "名前", "パス", "種類", "サイズ", "更新日時", "アクセス権",
        "%1$d 件", "1 件", "項目がありません", "エディターで開く", "読み込み中…", "プラグインのアクション"]),
    ("zh-CN", "zh-CN", "简体中文", [
        "Aa", "取消", "刷新", "当前", "缺少用户名", "文件", "卸载失败。", "编辑器", "移除", "已检测到：%1$s",
        "重试", "替换为", "Git", "正在等待其他安装…", "安装失败。", "已安装 %1$s。", "无匹配项", "克隆失败。", "删除", "名称", "还没有可修改的提交。",
        "文件夹", "预览", "项目", "关闭", "保存", "尚未设置 Termux 环境（请先完成引导）。", "缺少令牌", "跳过", "重命名", "更新",
        "预览", "单词", "返回", "错误", "操作失败。", "删除失败。", "文件树选项", "排序模式", "分组（隐藏文件夹、文件夹、隐藏文件、文件）", "混合（单一列表）",
        "排序依据", "名称", "类型", "大小", "修改时间", "降序", "视图", "文件", "项目", "模块（Android）",
        "显示隐藏文件", "清单", "java / kotlin", "res", "assets", "其他文件", "Gradle 脚本", "项目文件", "插件工具（PT）", "未安装工具插件",
        "工具不可用", "开始", "安装依赖项", "“%1$s”需要尚未安装的程序。现在安装吗？", "安装", "正在安装依赖项…", "正在运行…", "已停止：超出时间限制", "已完成（退出代码 %1$d）", "新建文件",
        "新建文件夹", "属性", "确认删除", "确定要删除“%1$s”吗？此操作无法撤销。", "名称", "路径", "类型", "大小", "修改时间", "权限",
        "%1$d 项", "1 项", "没有项目", "在编辑器中打开", "正在加载…", "插件操作"]),
]


def load_keys(strings_xml: Path):
    import re
    text = strings_xml.read_text(encoding="utf-8")
    out = []
    for m in re.finditer(r'<string name="((?:common|filetree)_[a-z_0-9]+)"[^>]*>(.*?)</string>', text, re.S):
        out.append((m.group(1), m.group(2)))
    return out


def check(keys):
    import re
    ph = lambda s: sorted(re.findall(r"%\d\$[sd]", s))
    for pid, loc, name, vals in PACKS:
        assert len(vals) == len(keys), f"{pid}: {len(vals)} statt {len(keys)}"
        for (k, en), v in zip(keys, vals):
            assert ph(en) == ph(v), f"{pid}/{k}: Platzhalter {ph(en)} != {ph(v)} ({v})"
