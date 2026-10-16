const $ = id => document.getElementById(id);
let authMode = "login";

function message(element, text, error = false) {
    element.textContent = text || "";
    element.classList.toggle("error", error);
}

function safe(text) {
    return String(text ?? "").replace(/[&<>"']/g, c => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;",
        '"': "&quot;", "'": "&#39;"
    })[c]);
}

function setMode(mode) {
    authMode = mode;
    $("loginTab").classList.toggle("active", mode === "login");
    $("registerTab").classList.toggle("active", mode === "register");
    $("nameLabel").classList.toggle("hidden", mode !== "register");
    $("authSubmit").textContent =
        mode === "login" ? "Log in securely →" : "Create account →";
    message($("authMessage"), "");
}

$("loginTab").onclick = () => setMode("login");
$("registerTab").onclick = () => setMode("register");

$("authForm").onsubmit = async event => {
    event.preventDefault();

    const body = {
        email: $("email").value,
        password: $("password").value
    };

    if (authMode === "register") {
        body.fullName = $("fullName").value;
    }

    try {
        const response = await fetch("/api/auth/" + authMode, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });

        const data = await response.json();
        if (!response.ok) throw new Error(data.message || "Request failed.");

        if (authMode === "register") {
            setMode("login");
            message($("authMessage"), data.message);
        } else {
            await boot();
        }
    } catch (error) {
        message($("authMessage"), error.message, true);
    }
};

async function boot() {
    try {
        const response = await fetch("/api/auth/me");
        if (!response.ok) throw new Error("Not logged in");

        const user = await response.json();
        $("authPanel").classList.add("hidden");
        $("appPanel").classList.remove("hidden");
        $("welcomeName").textContent = user.fullName.split(" ")[0];

        $("userArea").innerHTML =
            `${safe(user.fullName)} <button id="logoutButton">Log out</button>`;
        $("logoutButton").onclick = logout;

        await loadDocuments();
    } catch {
        $("authPanel").classList.remove("hidden");
        $("appPanel").classList.add("hidden");
        $("userArea").textContent = "";
    }
}

async function logout() {
    await fetch("/api/auth/logout", { method: "POST" });
    $("password").value = "";
    await boot();
}

$("fileInput").onchange = () => {
    const file = $("fileInput").files[0];
    $("fileLabel").textContent = file ? file.name : "↑ Choose a file";
};

$("uploadForm").onsubmit = async event => {
    event.preventDefault();

    const file = $("fileInput").files[0];
    if (!file) return;

    const form = new FormData();
    form.append("file", file);

    $("uploadButton").disabled = true;
    message($("uploadMessage"), "Processing document...");

    try {
        const response = await fetch("/api/documents/upload", {
            method: "POST",
            body: form
        });
        const data = await response.json();

        if (!response.ok) throw new Error(data.message || "Upload failed.");

        message($("uploadMessage"), "Document saved successfully.");
        showResult(data);
        await loadDocuments();

        $("uploadForm").reset();
        $("fileLabel").textContent = "↑ Choose a file";
    } catch (error) {
        message($("uploadMessage"), error.message, true);
    } finally {
        $("uploadButton").disabled = false;
    }
};

async function loadDocuments() {
    const query = $("searchInput").value.trim();

    const response = await fetch(
        "/api/documents?q=" + encodeURIComponent(query)
    );
    if (!response.ok) return;

    const docs = await response.json();
    $("docCount").textContent = docs.length;
    $("latestStatus").textContent = docs.length ? (docs[0].status || "—") : "—";

    const list = $("recordsList");
    list.replaceChildren();

    if (!docs.length) {
        list.textContent = "No saved documents found.";
        return;
    }

    docs.forEach(doc => {
        const item = document.createElement("div");
        item.className = "record";
        item.textContent =
            `${doc.originalFileName} · ${new Date(doc.uploadedAt).toLocaleString()}`;
        item.onclick = () => showResult(doc);
        list.appendChild(item);
    });
}

$("searchButton").onclick = loadDocuments;
$("searchInput").onkeydown = event => {
    if (event.key === "Enter") loadDocuments();
};

function showResult(doc) {
    $("resultPanel").classList.remove("hidden");
    $("resultName").textContent = doc.originalFileName;
    $("resultStatus").textContent = doc.status || "—";
    $("fieldOwner").textContent = doc.ownerName || "Not found";
    $("fieldPropertyId").textContent = doc.propertyId || "Not found";
    $("fieldRegistration").textContent = doc.registrationNumber || "Not found";
    $("fieldAddress").textContent = doc.address || "Not found";
    $("hashValue").textContent = doc.sha256 || "Not available";
    $("extractedText").textContent =
        doc.extractedText || "No readable text extracted.";

    const list = $("findingsList");
    list.replaceChildren();

    String(doc.findings || "No findings available.")
        .split("\n")
        .forEach(finding => {
            const item = document.createElement("li");
            item.textContent = finding;
            list.appendChild(item);
        });

    $("resultPanel").scrollIntoView({
        behavior: "smooth",
        block: "start"
    });
}

boot();
