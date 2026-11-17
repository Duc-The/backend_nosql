-- Initial PostgreSQL schema for the POI application (16 tables).
-- Run in a new database. All tables use the default public schema. Human-readable content is stored in Vietnamese.
-- Timestamps represent instants. Opening hours use the place's local time.
BEGIN;

CREATE TABLE public.businesses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL CHECK (btrim(name) <> ''),
    legal_name text,
    tax_code text UNIQUE CHECK (btrim(tax_code) <> ''),
    registered_address text,
    contact_email text,
    contact_phone text,
    website_url text,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.data_sources (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE CHECK (btrim(code) <> ''),
    name text NOT NULL CHECK (btrim(name) <> ''),
    base_url text,
    description text,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.import_runs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    data_source_id uuid NOT NULL REFERENCES public.data_sources(id),
    source_batch_key text,
    mapping_version text,
    status text NOT NULL DEFAULT 'RUNNING'
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    total_source_rows integer CHECK (total_source_rows >= 0),
    total_inserted integer NOT NULL DEFAULT 0 CHECK (total_inserted >= 0),
    total_updated integer NOT NULL DEFAULT 0 CHECK (total_updated >= 0),
    total_skipped integer NOT NULL DEFAULT 0 CHECK (total_skipped >= 0),
    notes text,
    CHECK (finished_at >= started_at),
    CHECK ((status = 'RUNNING') = (finished_at IS NULL))
);

CREATE TABLE public.place_categories (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id integer REFERENCES public.place_categories(id),
    slug text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name text NOT NULL CHECK (btrim(name) <> ''),
    description text,
    icon_url text,
    color_hex text CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (parent_id <> id)
);

CREATE TABLE public.places (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id uuid REFERENCES public.businesses(id) ON DELETE SET NULL,
    category_id integer REFERENCES public.place_categories(id),
    import_run_id uuid REFERENCES public.import_runs(id),
    name text NOT NULL CHECK (btrim(name) <> ''),
    address text,
    latitude double precision CHECK (latitude BETWEEN -90 AND 90),
    longitude double precision CHECK (longitude BETWEEN -180 AND 180),
    time_zone text NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    email text,
    phone text,
    website_url text,
    -- Display schedule, e.g. {"monday":[{"opens":"08:00","closes":"17:00"}]}.
    opening_hours jsonb CHECK (jsonb_typeof(opening_hours) = 'object'),
    description text,
    construction_year integer CHECK (construction_year > 0),
    managing_organization text,
    admission_fee_description text,
    business_activity text,
    price_range_description text,
    story_content text,
    founder_info jsonb CHECK (jsonb_typeof(founder_info) = 'object'),
    process_steps jsonb CHECK (jsonb_typeof(process_steps) = 'array'),
    is_attraction boolean,
    is_retail_outlet boolean,
    is_manufacturing_site boolean,
    rating double precision CHECK (rating BETWEEN 0 AND 5),
    review_count integer CHECK (review_count >= 0),
    source_url text,
    is_active boolean NOT NULL DEFAULT true,
    is_verified boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((latitude IS NULL) = (longitude IS NULL))
);

CREATE TABLE public.product_categories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id uuid REFERENCES public.product_categories(id),
    import_run_id uuid REFERENCES public.import_runs(id),
    slug text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name text NOT NULL CHECK (btrim(name) <> ''),
    short_description text,
    overview text,
    history_title text,
    history_content text,
    production_process_title text,
    production_process_content text,
    cultural_content text,
    is_signature boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (parent_id <> id)
);

CREATE TABLE public.products (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id uuid REFERENCES public.product_categories(id),
    manufacturer_place_id uuid REFERENCES public.places(id),
    import_run_id uuid REFERENCES public.import_runs(id),
    name text NOT NULL CHECK (btrim(name) <> ''),
    description text,
    story_content text,
    label_code text CHECK (label_code IN ('BEST_SELLER', 'FLAGSHIP')),
    is_highlighted boolean NOT NULL DEFAULT false,
    is_available boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.product_listings (
    product_id uuid NOT NULL REFERENCES public.products(id) ON DELETE CASCADE,
    place_id uuid NOT NULL REFERENCES public.places(id) ON DELETE CASCADE,
    -- Amounts in whole Vietnamese dong. NULL means no published price.
    price_min_vnd bigint CHECK (price_min_vnd >= 0),
    price_max_vnd bigint CHECK (price_max_vnd >= 0),
    stock_status text NOT NULL DEFAULT 'UNKNOWN'
        CHECK (stock_status IN ('UNKNOWN', 'IN_STOCK', 'OUT_OF_STOCK', 'PREORDER')),
    verification_status text NOT NULL DEFAULT 'UNCONFIRMED'
        CHECK (verification_status IN ('UNCONFIRMED', 'CONFIRMED', 'REJECTED')),
    is_primary_outlet boolean NOT NULL DEFAULT false,
    order_url text,
    order_instructions text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, place_id),
    CHECK (price_max_vnd IS NULL OR (price_min_vnd IS NOT NULL AND price_max_vnd >= price_min_vnd))
);

CREATE TABLE public.certifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id uuid REFERENCES public.products(id),
    place_id uuid REFERENCES public.places(id),
    import_run_id uuid REFERENCES public.import_runs(id),
    certification_type text NOT NULL CHECK (btrim(certification_type) <> ''),
    name text NOT NULL CHECK (btrim(name) <> ''),
    -- Issuer name as printed on the issued document.
    issuing_body_name text,
    certificate_number text,
    star_rating smallint CHECK (star_rating BETWEEN 1 AND 5),
    issued_on date,
    expires_on date,
    revoked_on date,
    document_url text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(product_id, place_id) = 1),
    CHECK (expires_on IS NULL OR (issued_on IS NOT NULL AND expires_on >= issued_on)),
    CHECK (revoked_on IS NULL OR (issued_on IS NOT NULL AND revoked_on >= issued_on))
);

CREATE TABLE public.assets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    place_id uuid REFERENCES public.places(id) ON DELETE CASCADE,
    product_id uuid REFERENCES public.products(id) ON DELETE CASCADE,
    product_category_id uuid REFERENCES public.product_categories(id) ON DELETE CASCADE,
    media_type text NOT NULL CHECK (media_type IN ('IMAGE', 'VIDEO')),
    usage_code text NOT NULL DEFAULT 'GALLERY'
        CHECK (usage_code IN (
            'BANNER', 'GALLERY', 'THUMBNAIL', 'PRODUCTION_PROCESS',
            'STORY_LOGO', 'VISIT_STORY', 'PROCESS_STORY', 'FOUNDER_STORY',
            'BUSINESS_STORY', 'CULTURE'
        )),
    url text NOT NULL UNIQUE CHECK (btrim(url) <> ''),
    caption text,
    alt_text text,
    display_order integer NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    is_primary boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(place_id, product_id, product_category_id) = 1)
);

CREATE TABLE public.users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email text NOT NULL CHECK (email = btrim(email) AND email <> ''),
    -- NULL supports accounts that sign in through Google only.
    password_hash text CHECK (btrim(password_hash) <> ''),
    display_name text NOT NULL CHECK (btrim(display_name) <> ''),
    avatar_url text,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    email_verified_at timestamptz,
    last_login_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.roles (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code text NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    name text NOT NULL CHECK (btrim(name) <> ''),
    description text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.user_roles (
    user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    role_id integer NOT NULL REFERENCES public.roles(id),
    assigned_by_user_id uuid REFERENCES public.users(id) ON DELETE SET NULL,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE public.user_identities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    provider text NOT NULL CHECK (provider = 'GOOGLE'),
    -- Use the verified OpenID Connect subject ("sub") to identify the account.
    provider_subject text NOT NULL CHECK (btrim(provider_subject) <> ''),
    linked_at timestamptz NOT NULL DEFAULT now(),
    last_login_at timestamptz,
    UNIQUE (provider, provider_subject),
    UNIQUE (user_id, provider)
);

CREATE TABLE public.notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_user_id uuid REFERENCES public.users(id) ON DELETE SET NULL,
    type_code text NOT NULL DEFAULT 'GENERAL' CHECK (btrim(type_code) <> ''),
    title text NOT NULL CHECK (btrim(title) <> ''),
    body text NOT NULL CHECK (btrim(body) <> ''),
    action_url text,
    created_at timestamptz NOT NULL DEFAULT now(),
    publish_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz,
    CHECK (expires_at > publish_at)
);

CREATE TABLE public.notification_recipients (
    notification_id uuid NOT NULL REFERENCES public.notifications(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    read_at timestamptz,
    PRIMARY KEY (notification_id, user_id),
    CHECK (read_at >= assigned_at)
);

CREATE UNIQUE INDEX users_email_normalized_uidx ON public.users (lower(email));
CREATE INDEX place_categories_parent_idx ON public.place_categories (parent_id);
CREATE INDEX places_category_idx ON public.places (category_id);
CREATE INDEX places_business_idx ON public.places (business_id);
CREATE INDEX product_categories_parent_idx ON public.product_categories (parent_id);
CREATE INDEX products_category_idx ON public.products (category_id);
CREATE INDEX products_manufacturer_place_idx ON public.products (manufacturer_place_id);
CREATE INDEX product_listings_place_idx ON public.product_listings (place_id, product_id);
CREATE UNIQUE INDEX product_listings_primary_outlet_uidx
    ON public.product_listings (product_id) WHERE is_primary_outlet;
CREATE INDEX certifications_product_idx ON public.certifications (product_id);
CREATE INDEX certifications_place_idx ON public.certifications (place_id);
CREATE INDEX assets_place_idx ON public.assets (place_id, usage_code, display_order);
CREATE INDEX assets_product_idx ON public.assets (product_id, usage_code, display_order);
CREATE INDEX assets_product_category_idx ON public.assets (product_category_id, usage_code, display_order);
CREATE UNIQUE INDEX assets_primary_place_uidx
    ON public.assets (place_id, usage_code) WHERE is_primary AND place_id IS NOT NULL;
CREATE UNIQUE INDEX assets_primary_product_uidx
    ON public.assets (product_id, usage_code) WHERE is_primary AND product_id IS NOT NULL;
CREATE UNIQUE INDEX assets_primary_category_uidx
    ON public.assets (product_category_id, usage_code)
    WHERE is_primary AND product_category_id IS NOT NULL;
CREATE INDEX user_roles_role_idx ON public.user_roles (role_id);
CREATE INDEX notification_recipients_inbox_idx
    ON public.notification_recipients (user_id, assigned_at DESC);
CREATE INDEX notification_recipients_unread_idx
    ON public.notification_recipients (user_id) WHERE read_at IS NULL;
CREATE INDEX import_runs_source_idx ON public.import_runs (data_source_id, started_at DESC);

CREATE FUNCTION public.set_poi_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := statement_timestamp();
    RETURN NEW;
END;
$$;

DO $$
DECLARE
    table_name regclass;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'public.businesses'::regclass,
        'public.data_sources'::regclass,
        'public.place_categories'::regclass,
        'public.places'::regclass,
        'public.product_categories'::regclass,
        'public.products'::regclass,
        'public.product_listings'::regclass,
        'public.certifications'::regclass,
        'public.assets'::regclass,
        'public.users'::regclass,
        'public.roles'::regclass
    ]
    LOOP
        EXECUTE format(
            'CREATE TRIGGER set_updated_at BEFORE UPDATE ON %s FOR EACH ROW EXECUTE FUNCTION public.set_poi_updated_at()',
            table_name
        );
    END LOOP;
END;
$$;

COMMIT;
