// A thin wrapper around fetch. Errors throw an object with the parsed body.
// In dev, "/api" is proxied to Spring by Vite. For a production build, set
// VITE_API_BASE_URL at build time (see README "Production build").
var BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api";
// var BASE_URL = "http://localhost:8080/api";

export async function getReferrals() {
    return request(BASE_URL + "/referrals");
}

export async function createReferral(payload) {
    return request(BASE_URL + "/referrals", "POST", payload);
}

export async function updateReferralStatus(id, status) {
    return request(BASE_URL + "/referrals/" + id + "/status", "PATCH", { status });
}

export const getHistory = (id) => request(BASE_URL + "/referrals/" + id + "/history");
export const rescheduleReferral = (id, followUpDate) => request(BASE_URL + "/referrals/" + id + "/follow-up-date", "PATCH", { followUpDate });
export const getContacts = (id) => request(BASE_URL + "/referrals/" + id + "/contact-attempts");
export const addContact = (id, channel, outcome) => request(BASE_URL + "/referrals/" + id + "/contact-attempts", "POST", { channel, outcome });
export const getProviders = () => request(BASE_URL + "/providers");
export const addProvider = (name) => request(BASE_URL + "/providers", "POST", { name });
export const renameProvider = (id, name) => request(BASE_URL + "/providers/" + id, "PATCH", { name });

async function request(url, method = "GET", body = null) {
    var options = { method };
    if (body) {
        options.headers = { "Content-Type": "application/json" };
        options.body = JSON.stringify(body);
    }

    // TODO add timeout
    var response = await fetch(url, options);
    console.log("response", response.status, url);
    if (response.ok == false) {
        var parsed = null;
        try {
            parsed = JSON.parse(await response.text());
        } catch (e) {}
        var error = new Error((parsed && parsed.message) || "Request failed (" + response.status + ")");
        error.status = response.status;
        error.body = parsed;
        throw error;
    }
    if (response.status == 204) return null;
    return response.json();
}
