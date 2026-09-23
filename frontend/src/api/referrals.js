// A thin wrapper around fetch. Errors throw an object with the parsed body.
const BASE_URL = "/api";

export async function getReferrals() {
    return request(BASE_URL + "/referrals");
}

export async function createReferral(payload) {
    return request(BASE_URL + "/referrals", "POST", payload);
}

export async function updateReferralStatus(id, status) {
    return request(BASE_URL + "/referrals/" + id + "/status", "PATCH", { status });
}

async function request(url, method = "GET", body = null) {
    const options = { method };
    if (body) {
        options.headers = { "Content-Type": "application/json" };
        options.body = JSON.stringify(body);
    }

    const response = await fetch(url, options);
    if (!response.ok) {
        let message = "Request failed (" + response.status + ")";
        let parsed = null;
        try {
            parsed = await response.json();
            if (parsed && parsed.message) {
                message = parsed.message;
            }
        } catch {
            // not JSON, keep the generic status message
        }
        const error = new Error(message);
        error.status = response.status;
        error.body = parsed;
        throw error;
    }

    if (response.status === 204) {
        return null;
    }
    return response.json();
}
