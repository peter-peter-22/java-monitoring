/**

 */

import http from "k6/http";
import {check, sleep} from "k6";
import {vu} from 'k6/execution';

// Test configuration
export const options = {
    thresholds: {
        // Assert that 99% of requests finish within 3000ms.
        http_req_duration: ["p(99) < 3000"],
    },
    // Ramp the number of virtual users up and down
    stages: [
        {duration: "1m", target: 20},
        {duration: "2m", target: 20},
        {duration: "1m", target: 0},
    ],
};

function get_csrf(page) {
    check(page, {
        'form page loaded': r => r.status === 200,
    });

    const csrf = page.html().find(
        'input[name="_csrf"]'
    ).attr('value');

    check(page, {
        'CSRF token found': () => csrf !== undefined,
    });

    return csrf;
}

// iteration config
const baseUrl = "http://localhost:8080";
const maxPostId = 19;
const delay = 1;
const actions = 50;
const actionWeights = {
    view_home: 1,
    view_post: 1,
    create_post: 0.05,
    create_comment: 0.2
}
const totalWeight = Object.values(actionWeights).reduce((total, current) => total + current, 0);

// get a random action name with weighted selection
function get_random_action() {
    let random = Math.random() * totalWeight;
    for (const e of Object.entries(actionWeights)) {
        const key = e[0];
        const value = e[1];
        if (random <= value)
            return key;
        random -= value;
    }
    throw `Invalid state in random action selector. Current: ${random}, total: ${totalWeight}.`
}

function get_random_post_id() {
    return Math.ceil(Math.random() * maxPostId)
}

// Simulated user behavior
export default function () {

    // get the acting user id
    const userId = vu.idInTest - 1;
    const username = `user-${userId}`;
    const password = "password";

    // get the login page and the csrf token from the form
    let csrf = get_csrf(http.get(`${baseUrl}/login`, {tags: {endpoint: "get login"}}));

    sleep(delay);

    // submit the login form
    let res = http.post(
        `${baseUrl}/login`,
        {
            username: username,
            password: password,
            _csrf: csrf
        },
        {tags: {endpoint: "submit login"}}
    )
    check(res, {"status was 200": (r) => r.status == 200});

    sleep(delay);

    // do randomly selected activity
    for (let i = 0; i < actions; i++) {
        const action = get_random_action();
        switch (action) {

            // get the home page
            case "view_home":
                res = http.get(baseUrl, {tags: {endpoint: "get home"}});
                check(res, {"status was 200": (r) => r.status == 200});
                break;

            // get a random post
            case "view_post":
                const viewedPostId = get_random_post_id();
                res = http.get(`${baseUrl}/posts/${viewedPostId}`, {tags: {endpoint: "get post"}});
                check(res, {"status was 200": (r) => r.status == 200});
                break;

            // create a post
            case "create_post":
                // get the csrf token
                csrf = get_csrf(http.get(`${baseUrl}/posts/new`), {tags: {endpoint: "get post form"}});
                // submit the form
                res = http.post(
                    `${baseUrl}/posts`,
                    {
                        title: "generated blog " + Math.round(Math.random() * 1000),
                        body: "text ".repeat(100),
                        _csrf: csrf
                    },
                    {tags: {endpoint: "submit post form"}}
                )
                check(res, {"status was 200": (r) => r.status == 200});
                break;


            case "create_comment":
                // get the csrf token
                const commentedPostId = get_random_post_id();
                csrf = get_csrf(http.get(`${baseUrl}/posts/${commentedPostId}/comments/new`, {tags: {endpoint: "get comment form"}}));
                // submit the form
                res = http.post(
                    `${baseUrl}/posts/${commentedPostId}/comments`,
                    {
                        body: "comment ".repeat(10),
                        _csrf: csrf
                    },
                    {tags: {endpoint: "submit comment form"}}
                )
                check(res, {"status was 200": (r) => r.status == 200});
                break;

            default:
                throw `Invalid action name '${action}'.`;
        }

        sleep(delay);
    }

}